package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.StorageQuota
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.domain.UserUploadEntity
import fi.lipp.blog.model.exceptions.DailyUploadLimitExceededException
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.model.exceptions.InvalidAvatarExtensionException
import fi.lipp.blog.model.exceptions.InvalidAvatarDimensionsException
import fi.lipp.blog.model.exceptions.InvalidAvatarSizeException
import fi.lipp.blog.model.exceptions.InvalidReactionImageException
import fi.lipp.blog.repository.*
import javax.imageio.ImageIO
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.and
import java.io.File
import java.nio.file.Path
import java.util.UUID

class StorageServiceImpl(private val properties: ApplicationProperties): StorageService {
    override fun store(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, files) { it }
    }

    override fun storeAvatars(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, files) { file ->
           validateAvatar(file)
        }
    }

    override fun storeReaction(userId: UUID, fileName: String, file: FileUploadData): BlogFile {
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, listOf(file), fileName) { file ->
            validateReaction(file)
        }[0]
    }

    private fun getUserLogin(userId: UUID): String {
        return transaction { DiaryEntity.find { Diaries.owner eq userId }.singleOrNull()?.login ?: throw InternalServerError() }
    }

    // TODO safer avatar storing. Only the given extensions with size & dimensions limits
    private val allowedImageExtensions = setOf(".jpg", ".jpeg", ".png", ".gif", ".svg", ".webp")
    private fun validateAvatar(file: FileUploadData): FileUploadData {
        if (!allowedImageExtensions.contains(file.extension)) throw InvalidAvatarExtensionException()

        // Read the entire input stream into a byte array
        val bytes = file.inputStream.readAllBytes()

        // Check if file size is less than 1MB
        if (bytes.size > 1_048_576) {
            throw InvalidAvatarSizeException()
        }

        val image = ImageIO.read(bytes.inputStream())

        if (image.width != image.height) {
            throw InvalidAvatarDimensionsException()
        }

        // Create a new FileUploadData with a fresh InputStream from the bytes
        return FileUploadData(
            fullName = file.fullName,
            inputStream = bytes.inputStream()
        )
    }

    private fun validateReaction(file: FileUploadData): FileUploadData {
        if (!allowedImageExtensions.contains(file.extension)) throw InvalidReactionImageException()

        // Read the entire input stream into a byte array
        val bytes = file.inputStream.readAllBytes()

        // Check if file size is less than 512KB
        if (bytes.size > 512 * 1024) {
            throw InvalidReactionImageException()
        }

        val image = ImageIO.read(bytes.inputStream())

        // Check if image is square and dimensions are <= 100x100
//        if (image.width != image.height || image.width > 100) {
//            throw InvalidReactionImageException()
//        }

        // Create a new FileUploadData with a fresh InputStream from the bytes
        return FileUploadData(
            fullName = file.fullName,
            inputStream = bytes.inputStream()
        ).apply { 
            type = FileType.REACTION 
        }
    }

    override fun getFile(file: BlogFile): File {
        val userLogin = transaction { 
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            DiaryEntity.find { Diaries.owner eq fileEntity.owner.value }.singleOrNull()?.login ?: throw InternalServerError()
        }
        val path = getSavingPath(userLogin, file.type)
        return File("$path/${file.name}")
    }

    private fun getUserQuota(userId: UUID): StorageQuota {
        return transaction {
            UserEntity.findById(userId)?.storageQuota ?: StorageQuota.BASIC
        }
    }

    private fun store(userId: UUID, userLogin: String, files: List<FileUploadData>, fileName: String? = null, performChecks: (FileUploadData) -> FileUploadData): List<BlogFile> {
        val blogFiles = mutableListOf<BlogFile>()
        transaction {
            val validatedFiles = files.map { performChecks(it) }
            val totalSize = validatedFiles.sumOf { it.inputStream.available().toLong() }

            val currentUpload = getDailyUpload(userId)
            val userQuota = getUserQuota(userId)
            val dailyLimit = userQuota.getDailyLimitBytes(properties)

            if (dailyLimit != null) {
                val newTotal = currentUpload + totalSize
                if (newTotal > dailyLimit) {
                    throw DailyUploadLimitExceededException(dailyLimit, newTotal)
                }
            }

            validatedFiles.forEach { file ->
                val fileName = fileName ?: (UUID.randomUUID().toString() + file.extension)
                val fileId = Files.insertAndGetId {
                    it[name] = fileName
                    it[owner] = userId
                    it[fileType] = file.type
                }
                val blogFile = createFile(userId, userLogin, fileId.value, fileName, file)
                blogFiles.add(blogFile)
            }

            trackUpload(userId, totalSize)
        }
        return blogFiles.toMutableList()
    }

    private fun ensureDirectoryExists(path: Path) {
        val directory = path.toFile()
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw InternalServerError()
            }
        } else if (!directory.isDirectory) {
            throw InternalServerError()
        }
    }

    private fun createFile(userId: UUID, userLogin: String, fileId: UUID, fileName: String, fileUploadData: FileUploadData): BlogFile {
        val path = getSavingPath(userLogin, fileUploadData.type)

        ensureDirectoryExists(path)

        val file = path.resolve(fileName).toFile()
        fileUploadData.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return BlogFile(fileId, userId, fileName, fileUploadData.type)
    }

    override fun getFileURL(file: BlogFile): String {
        val userLogin = transaction {
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            DiaryEntity.find { (Diaries.owner eq fileEntity.owner.value) and (Diaries.type eq DiaryType.PERSONAL) }
                .singleOrNull()?.login ?: throw InternalServerError()
        }
        val url = when (file.type) {
            FileType.AVATAR -> properties.avatarsUrl(userLogin)
            FileType.IMAGE -> properties.imagesUrl(userLogin)
            FileType.VIDEO -> properties.videosUrl(userLogin)
            FileType.AUDIO -> properties.audiosUrl(userLogin)
            FileType.STYLE -> properties.stylesUrl(userLogin)
            FileType.OTHER -> properties.otherUrl(userLogin)
            FileType.REACTION -> properties.reactionsUrl(userLogin)
        }
        return "$url/${file.name}"
    }

    private fun getDailyUpload(userId: UUID): Long {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return UserUploadEntity.find { 
            (UserUploads.user eq userId) and (UserUploads.date eq today)
        }.firstOrNull()?.totalBytes ?: 0
    }

    private fun trackUpload(userId: UUID, size: Long) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val upload = UserUploadEntity.find { 
            (UserUploads.user eq userId) and (UserUploads.date eq today)
        }.firstOrNull()

        if (upload != null) {
            upload.totalBytes += size
        } else {
            UserUploadEntity.new {
                user = UserEntity[userId]
                date = today
                totalBytes = size
            }
        }
    }

    private fun getSavingPath(userLogin: String, fileType: FileType): Path {
        return when (fileType) {
            FileType.AVATAR -> properties.avatarsDirectory(userLogin)
            FileType.IMAGE -> properties.imagesDirectory(userLogin)
            FileType.VIDEO -> properties.videosDirectory(userLogin)
            FileType.AUDIO -> properties.audiosDirectory(userLogin)
            FileType.STYLE -> properties.stylesDirectory(userLogin)
            FileType.OTHER -> properties.otherDirectory(userLogin)
            FileType.REACTION -> properties.reactionsDirectory(userLogin)
        }
    }
}
