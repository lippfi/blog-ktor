package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import javax.imageio.ImageIO
import java.nio.file.Files as JFiles

class StorageServiceImpl(private val properties: ApplicationProperties): StorageService {
    override fun store(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        return store(userId, files) { it }
    }

    override fun storeAvatars(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        return store(userId, files) { file ->
           validateAvatar(file)
        }
    }

    override fun storeReaction(userId: UUID, fileName: String, file: FileUploadData): BlogFile {
        val validated = validateReaction(file)

        val exists = transaction {
            Files.select { (Files.fileType eq FileType.REACTION) and (Files.name eq fileName) }
                .limit(1)
                .empty().not()
        }
        if (exists) throw ReactionAlreadyExistsException()

        return store(userId, listOf(validated), fileName) { it }.single()
    }

    private val allowedImageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")

    private fun validateAvatar(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidAvatarExtensionException()
        if (ext !in allowedImageExtensions) throw InvalidAvatarExtensionException()

        val bytes = file.inputStream.readAllBytes()
        if (bytes.size > 1_048_576) throw InvalidAvatarSizeException()

        val image = ImageIO.read(bytes.inputStream()) ?: throw InvalidAvatarExtensionException()
        if (image.width != image.height) throw InvalidAvatarDimensionsException()

        return FileUploadData(fullName = file.fullName, inputStream = bytes.inputStream())
    }

    private fun validateReaction(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidReactionImageException()
        if (ext !in allowedImageExtensions) throw InvalidReactionImageException()

        val bytes = file.inputStream.readAllBytes()
        if (bytes.size > 512 * 1024) throw InvalidReactionImageException()

        return FileUploadData(fullName = file.fullName, inputStream = bytes.inputStream(), forcedType = FileType.REACTION)
    }

    override fun getFile(file: BlogFile): File {
        val storageKey = transaction {
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            fileEntity.storageKey
        }
        return resolveStoragePath(storageKey).toFile()
    }

    private fun sha256HexFromPath(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1024 * 64)
        JFiles.newInputStream(path).use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun store(
        userId: UUID,
        files: List<FileUploadData>,
        fileName: String? = null,
        performChecks: (FileUploadData) -> FileUploadData
    ): List<BlogFile> {
        val blogFiles = mutableListOf<BlogFile>()

        files.forEach { original ->
            val file = performChecks(original)

            val logicalName = when (file.type) {
                FileType.REACTION -> (fileName ?: file.name).ifBlank { UUID.randomUUID().toString() }
                else -> UUID.randomUUID().toString()
            }

            val fileId = transaction {
                Files.insertAndGetId {
                    it[name] = logicalName
                    it[owner] = userId
                    it[fileType] = file.type
                    it[mimeType] = file.mimeType
                    it[Files.ext] = file.ext
                    it[hash] = null
                    it[storageKey] = ""
                }.value
            }

            val typeFolder = file.type.name.lowercase()
            val physicalFileName = if (file.ext != null) "$fileId.${file.ext}" else fileId.toString()

            val storageKeyValue = if (file.type == FileType.REACTION) {
                "reactions/$physicalFileName"
            } else {
                "u/$userId/$typeFolder/$physicalFileName"
            }

            val savedPath: Path = try {
                val blogFile = createFile(
                    userId = userId,
                    fileId = fileId,
                    storageKey = storageKeyValue,
                    fileUploadData = file
                )
                blogFiles.add(blogFile)
                resolveStoragePath(storageKeyValue)
            } catch (e: Exception) {
                transaction {
                    Files.deleteWhere { Files.id eq fileId }
                }
                throw e
            }

            val h = try {
                sha256HexFromPath(savedPath)
            } catch (e: Exception) {
                runCatching { savedPath.toFile().delete() }
                transaction {
                    Files.deleteWhere { Files.id eq fileId }
                }
                throw e
            }

            try {
                transaction {
                    Files.update({ Files.id eq fileId }) {
                        it[storageKey] = storageKeyValue
                        it[hash] = h
                    }
                }
            } catch (e: Exception) {
                runCatching { savedPath.toFile().delete() }
                transaction {
                    Files.deleteWhere { Files.id eq fileId }
                }
                throw e
            }
        }

        return blogFiles.toList()
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

    private fun createFile(userId: UUID, fileId: UUID, storageKey: String, fileUploadData: FileUploadData): BlogFile {
        val targetPath = resolveStoragePath(storageKey)
        ensureDirectoryExists(targetPath.parent)

        val tmpPath = targetPath.resolveSibling("${targetPath.fileName}.tmp-${UUID.randomUUID()}")

        try {
            fileUploadData.inputStream.use { input ->
                tmpPath.toFile().outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            JFiles.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            runCatching { JFiles.deleteIfExists(tmpPath) }
            throw e
        }

        return BlogFile(fileId, userId, targetPath.fileName.toString(), fileUploadData.type)
    }

    private fun resolveStoragePath(storageKey: String): Path =
        properties.storageBaseDir().resolve(storageKey)

    override fun getFileURL(file: BlogFile): String {
        val storageKey = transaction {
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            fileEntity.storageKey
        }
        return "${properties.filesBaseUrl()}/$storageKey"
    }
}
