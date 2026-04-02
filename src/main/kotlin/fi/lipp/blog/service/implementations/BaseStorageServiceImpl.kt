package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.util.*
import javax.imageio.ImageIO

abstract class BaseStorageServiceImpl(protected val properties: ApplicationProperties) : StorageService {

    protected val allowedImageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")

    override fun store(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        return storeInternal(userId, files) { it }
    }

    override fun storeAvatars(userId: UUID, files: List<FileUploadData>): List<BlogFile> {
        return storeInternal(userId, files) { file ->
            validateAvatar(file)
        }
    }

    override fun storeReaction(userId: UUID, fileName: String, file: FileUploadData): BlogFile {
        val validated = validateReaction(file)
        return try {
            storeInternal(userId, listOf(validated), fileName) { it }.single()
        } catch (e: ExposedSQLException) {
            if (e.isUniqueViolation()) throw ReactionAlreadyExistsException()
            throw e
        }
    }

    override fun getFileURL(file: BlogFile): String {
        val storageKey = transaction {
            val fileEntity = FileEntity.findById(file.id) ?: throw InternalServerError()
            fileEntity.storageKey
        }
        return "${baseFileUrl()}/$storageKey"
    }

    override fun getFileURLs(files: Collection<BlogFile>): Map<UUID, String> {
        if (files.isEmpty()) return emptyMap()

        val fileIds = files.map { it.id }.distinct()
        val storageKeysById = transaction {
            Files
                .slice(Files.id, Files.storageKey)
                .select { Files.id inList fileIds }
                .associate { row ->
                    row[Files.id].value to row[Files.storageKey]
                }
        }

        return fileIds.associateWith { fileId ->
            val storageKey = storageKeysById[fileId] ?: throw InternalServerError()
            "${baseFileUrl()}/$storageKey"
        }
    }

    protected fun validateAvatar(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidAvatarExtensionException()
        if (ext !in allowedImageExtensions) throw InvalidAvatarExtensionException()
        if (file.bytes.size > 1_048_576) throw InvalidAvatarSizeException()

        val image = ImageIO.read(file.bytes.inputStream())
            ?: throw InvalidAvatarExtensionException()
        if (image.width != image.height) throw InvalidAvatarDimensionsException()

        return file
    }

    protected fun validateReaction(file: FileUploadData): FileUploadData {
        val ext = file.ext ?: throw InvalidReactionImageException()
        if (ext !in allowedImageExtensions) throw InvalidReactionImageException()
        if (file.bytes.size > 512 * 1024) throw InvalidReactionImageException()

        return file.copy(forcedType = FileType.REACTION)
    }

    protected data class PersistResult(val blogFile: BlogFile, val hash: String)

    protected abstract fun baseFileUrl(): String

    protected abstract fun persistFile(userId: UUID, fileId: UUID, storageKey: String, file: FileUploadData): PersistResult

    protected abstract fun cleanupFile(storageKey: String)

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
