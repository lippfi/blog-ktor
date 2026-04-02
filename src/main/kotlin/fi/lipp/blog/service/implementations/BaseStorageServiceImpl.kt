package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.util.*
import javax.imageio.ImageIO

abstract class BaseStorageServiceImpl(protected val properties: ApplicationProperties) : StorageService {

    protected val allowedImageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")

    override fun store(viewer: Viewer.Registered, files: List<FileUploadData>): List<BlogFile> {
        validateUploadPermissions(files, viewer.permissions)
        return storeInternal(viewer.userId, files) { file -> validateRegularFile(file) }
    }

    override fun storeAvatars(viewer: Viewer.Registered, files: List<FileUploadData>): List<BlogFile> {
        validateUploadPermissions(files, viewer.permissions)
        return storeInternal(viewer.userId, files) { file ->
            validateAvatar(file)
        }
    }

    override fun storeReaction(viewer: Viewer.Registered, fileName: String, file: FileUploadData): BlogFile {
        validateUploadPermissions(listOf(file), viewer.permissions)
        val validated = validateReaction(file)
        return try {
            storeInternal(viewer.userId, listOf(validated), fileName) { it }.single()
        } catch (e: ExposedSQLException) {
            if (e.isUniqueViolation()) throw ReactionAlreadyExistsException()
            throw e
        }
    }

    private fun validateUploadPermissions(files: List<FileUploadData>, permissions: Set<UserPermission>) {
        files.forEach { file ->
            val ext = file.ext?.lowercase()
            if (ext == "svg" && UserPermission.SVG_UPLOAD !in permissions) {
                throw SvgUploadNotAllowedException()
            }
        }
    }

    override fun getFileURL(file: BlogFile): String {
        return "${baseFileUrl()}/${file.storageKey}"
    }

    override fun getFileURLs(files: Collection<BlogFile>): Map<UUID, String> {
        return files
            .distinctBy { it.id }
            .associate { file ->
                file.id to "${baseFileUrl()}/${file.storageKey}"
            }
    }

    protected fun validateRegularFile(file: FileUploadData): FileUploadData {
        val maxSize = when (file.type) {
            FileType.IMAGE -> properties.maxImageSize
            FileType.VIDEO -> properties.maxVideoSize
            FileType.AUDIO -> properties.maxAudioSize
            FileType.STYLE -> properties.maxStyleSize
            FileType.OTHER -> properties.maxOtherSize
            FileType.AVATAR -> properties.maxAvatarSize
            FileType.REACTION -> properties.maxReactionSize
        }

        if (file.bytes.size > maxSize) {
            throw FileTooLargeException(file.type, maxSize)
        }

        return file
    }

    protected fun validateAvatar(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidAvatarExtensionException()
        if (ext !in allowedImageExtensions) throw InvalidAvatarExtensionException()
        if (file.bytes.size > properties.maxAvatarSize) throw InvalidAvatarSizeException()

        val image = ImageIO.read(file.bytes.inputStream())
            ?: throw InvalidAvatarExtensionException()
        if (image.width != image.height) throw InvalidAvatarDimensionsException()

        return file.copy(forcedType = FileType.AVATAR)
    }

    protected fun validateReaction(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidReactionImageException()
        if (ext !in allowedImageExtensions) throw InvalidReactionImageException()
        if (file.bytes.size > properties.maxReactionSize) throw InvalidReactionImageException()

        return file.copy(forcedType = FileType.REACTION)
    }

    protected data class PersistResult(val blogFile: BlogFile, val hash: String)

    protected abstract fun baseFileUrl(): String

    protected abstract fun persistFile(userId: UUID, fileId: UUID, storageKey: String, file: FileUploadData): PersistResult

    protected abstract fun cleanupFile(storageKey: String)

    override fun getStorageKeyByUrl(url: String): String {
        val cleanUrl = url.substringBefore('#').substringBefore('?')
        val base = baseFileUrl().removeSuffix("/")

        if (!cleanUrl.startsWith(base)) {
            throw InvalidAvatarUriException()
        }

        return cleanUrl
            .removePrefix(base)
            .removePrefix("/")
            .ifBlank { throw InvalidAvatarUriException() }
    }

    private fun storeInternal(
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

            val result = try {
                persistFile(userId, fileId, storageKeyValue, file)
            } catch (e: Exception) {
                transaction { Files.deleteWhere { Files.id eq fileId } }
                throw e
            }

            blogFiles.add(result.blogFile)

            try {
                transaction {
                    Files.update({ Files.id eq fileId }) {
                        it[storageKey] = storageKeyValue
                        it[hash] = result.hash
                    }
                }
            } catch (e: Exception) {
                cleanupFile(storageKeyValue)
                transaction { Files.deleteWhere { Files.id eq fileId } }
                throw e
            }
        }

        return blogFiles.toList()
    }

    fun Throwable.findSqlException(): SQLException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLException) return current
            current = current.cause
        }
        return null
    }

    fun ExposedSQLException.isUniqueViolation(): Boolean =
        findSqlException()?.sqlState == "23505"
}
