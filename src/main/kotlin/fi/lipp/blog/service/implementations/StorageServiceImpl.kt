package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.model.exceptions.InvalidAvatarExtensionException
import fi.lipp.blog.model.exceptions.InvalidReactionImageException
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Path
import java.util.UUID

class StorageServiceImpl(private val properties: ApplicationProperties): StorageService {
    override fun store(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, files) {}
    }

    override fun storeAvatars(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        // TODO store as AVATAR type
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, files) { file ->
           validateAvatar(file) 
        }
    }

    override fun storeReactions(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        val userLogin = getUserLogin(userId)
        return store(userId, userLogin, files) { file ->
            validateReaction(file)
        }
    }

    private fun getUserLogin(userId: UUID): String {
        return transaction { DiaryEntity.find { Diaries.owner eq userId }.singleOrNull()?.login ?: throw InternalServerError() }
    }

    // TODO safer avatar storing. Only the given extensions with size & dimensions limits
    private val allowedImageExtensions = setOf(".jpg", ".jpeg", ".png", ".gif")
    private fun validateAvatar(file: FileUploadData) {
        if (!allowedImageExtensions.contains(file.extension)) throw InvalidAvatarExtensionException()
        // TODO dirty code
        file.type = FileType.AVATAR
    }

    private fun validateReaction(file: FileUploadData) {
        if (!allowedImageExtensions.contains(file.extension)) throw InvalidReactionImageException()
        file.type = FileType.REACTION
    }

    override fun getFile(file: BlogFile): File {
        val userLogin = transaction { 
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            DiaryEntity.find { Diaries.owner eq fileEntity.owner.value }.singleOrNull()?.login ?: throw InternalServerError()
        }
        val path = getSavingPath(userLogin, file.type)
        return File("$path/${file.name}")
    }

    private fun store(userId: UUID, userLogin: String, files: List<FileUploadData>, performChecks: (FileUploadData) -> Unit): List<BlogFile> {
        val blogFiles = mutableListOf<BlogFile>()
        transaction {
            files.forEach { file ->
                performChecks(file)
                val uuid = Files.insertAndGetId {
                    it[owner] = userId
                    it[extension] = file.extension
                    it[fileType] = file.type
                }
                val blogFile = createFile(userId, userLogin, uuid.value, file)
                blogFiles.add(blogFile)
            }
        }
        return blogFiles
    }

    private fun createFile(userId: UUID, userLogin: String, uuid: UUID, fileUploadData: FileUploadData): BlogFile {
        val path = getSavingPath(userLogin, fileUploadData.type)
        val fileName = uuid.toString() + fileUploadData.extension

        File(path.toString()).mkdirs()

        val file = File("$path/$fileName")
        fileUploadData.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return BlogFile(uuid, userId, fileUploadData.extension, fileUploadData.type)
    }

    override fun getFileURL(file: BlogFile): String {
        val url = when (file.type) {
            FileType.AVATAR -> properties.avatarsUrl
            FileType.IMAGE -> properties.imagesUrl
            FileType.VIDEO -> properties.videosUrl
            FileType.AUDIO -> properties.audiosUrl
            FileType.STYLE -> properties.stylesUrl
            FileType.OTHER -> properties.otherUrl
            FileType.REACTION -> properties.reactionsUrl
        }
        return "$url/${file.name}"
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
