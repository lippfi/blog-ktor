package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.service.ApplicationProperties
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import java.nio.file.Files as JFiles

class LocalStorageServiceImpl(properties: ApplicationProperties) : BaseStorageServiceImpl(properties) {

    override fun baseFileUrl(): String = properties.filesBaseUrl()

    override fun getFile(file: BlogFile): File {
        val storageKey = transaction {
            FileEntity.findById(file.id)?.storageKey ?: throw InternalServerError()
        }
        return resolveStoragePath(storageKey).toFile()
    }

    override fun persistFile(userId: UUID, fileId: UUID, storageKey: String, file: FileUploadData): PersistResult {
        val targetPath = resolveStoragePath(storageKey)
        ensureDirectoryExists(targetPath.parent)

        val tmpPath = targetPath.resolveSibling("${targetPath.fileName}.tmp-${UUID.randomUUID()}")

        try {
            file.inputStream.use { input ->
                tmpPath.toFile().outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            JFiles.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            runCatching { JFiles.deleteIfExists(tmpPath) }
            throw e
        }

        val hash = sha256HexFromPath(targetPath)
        val blogFile = BlogFile(fileId, userId, targetPath.fileName.toString(), file.type)
        return PersistResult(blogFile, hash)
    }

    override fun cleanupFile(storageKey: String) {
        runCatching { resolveStoragePath(storageKey).toFile().delete() }
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

    private fun resolveStoragePath(storageKey: String): Path =
        properties.storageBaseDir().resolve(storageKey)

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
}
