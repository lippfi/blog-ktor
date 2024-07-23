package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.model.exceptions.InvalidAvatarExtensionException
import fi.lipp.blog.model.exceptions.InvalidFileExtension
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.UUID

class StorageServiceImpl(private val properties: ApplicationProperties): StorageService {
    // TODO safer avatar storing. Only the given extensions with size & dimensions limits
    private val allowedAvatarExtensions = setOf(".jpg", ".jpeg", ".png", ".gif")

    override fun store(userId: Long, files: List<FileUploadData>): List<BlogFile> {
        return store(userId, files) {}
    }

    override fun storeAvatars(userId: Long, files: List<FileUploadData>): List<BlogFile> {
        return store(userId, files) { file ->
            if (!allowedAvatarExtensions.contains(file.extension)) throw InvalidAvatarExtensionException()
        }
    }

    private fun store(userId: Long, files: List<FileUploadData>, performChecks: (FileUploadData) -> Unit): List<BlogFile> {
        val blogFiles = mutableListOf<BlogFile>()
        files.forEach { file ->
            performChecks(file)
            val uuid = transaction {
                Files.insertAndGetId {
                    it[owner] = userId
                    it[extension] = file.extension
                    it[fileType] = file.type
                }
            }
            val blogFile = createFile(userId, uuid.value, file)
            blogFiles.add(blogFile)
        }
        return blogFiles
    }

    private fun createFile(userId: Long, uuid: UUID, fileUploadData: FileUploadData): BlogFile {
        val path = getSavingPath(fileUploadData.type)
        val fileName = uuid.toString() + fileUploadData.extension
        val file = File("$path/$fileName")
        fileUploadData.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return BlogFile(uuid, userId, fileUploadData.extension, fileUploadData.type)
    }

    override fun getFileURL(file: BlogFile): URL {
        val url = when (file.type) {
            FileType.IMAGE -> properties.imagesUrl
            FileType.VIDEO -> properties.videosUrl
            FileType.AUDIO -> properties.audiosUrl
            FileType.STYLE -> properties.stylesUrl
            FileType.OTHER -> properties.otherUrl
        }
        return URL("$url/${file.name}")
    }

    private fun getSavingPath(fileType: FileType): Path {
        return when (fileType) {
            FileType.IMAGE -> properties.imagesDirectory
            FileType.VIDEO -> properties.videosDirectory
            FileType.AUDIO -> properties.audiosDirectory
            FileType.STYLE -> properties.stylesDirectory
            FileType.OTHER -> properties.otherDirectory
        }
    }
}