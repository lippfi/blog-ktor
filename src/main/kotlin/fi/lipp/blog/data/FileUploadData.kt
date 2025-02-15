package fi.lipp.blog.data

import fi.lipp.blog.util.lastIndexOfOrNull
import io.ktor.http.content.*
import java.io.InputStream

data class FileUploadData(
    val fullName: String,
    val inputStream: InputStream
) {
    val name = fullName.substringBeforeLast('.')
    val extension = fullName.lastIndexOfOrNull('.')?.let { fullName.substring(it) } ?: ""
    var type = getFileTypeByExtension(extension)

    private fun getFileTypeByExtension(extension: String): FileType {
        return when (extension) {
            ".jpg", ".jpeg", ".png", ".gif" -> FileType.IMAGE
            ".mp4", ".webm" -> FileType.VIDEO
            ".mp3" -> FileType.AUDIO
            ".css" -> FileType.STYLE
            else -> FileType.OTHER
        }
    }
}

suspend fun MultiPartData.toFileUploadDatas(): List<FileUploadData> {
    val result = mutableListOf<FileUploadData>()
    forEachPart { part ->
        if (part is PartData.FileItem) {
            val fileName = part.originalFileName ?: "no_name"
            val stream = part.streamProvider.invoke()

            val fileUploadData = FileUploadData(fullName = fileName, inputStream = stream)
            result.add(fileUploadData)
        }
    }
    return result
}
