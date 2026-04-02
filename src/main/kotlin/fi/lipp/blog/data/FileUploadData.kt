package fi.lipp.blog.data

import io.ktor.http.content.*
import java.io.InputStream

data class FileUploadData(
    val fullName: String,
    val bytes: ByteArray,
    val forcedType: FileType? = null
) {
    val name: String = fullName.substringBeforeLast('.', missingDelimiterValue = fullName)

    val ext: String? = fullName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.isNotBlank() }

    val mimeType: String = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "css" -> "text/css"
        else -> "application/octet-stream"
    }

    val type: FileType = forcedType ?: when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "svg" -> FileType.IMAGE
        "mp4", "webm" -> FileType.VIDEO
        "mp3" -> FileType.AUDIO
        "css" -> FileType.STYLE
        else -> FileType.OTHER
    }

    fun inputStream(): InputStream = bytes.inputStream()
}

suspend fun MultiPartData.toFileUploadDatas(): List<FileUploadData> {
    val result = mutableListOf<FileUploadData>()
    forEachPart { part ->
        try {
            if (part is PartData.FileItem) {
                val fileName = part.originalFileName ?: "no_name"
                val bytes = part.streamProvider().readBytes()

                val fileUploadData = FileUploadData(fullName = fileName, bytes = bytes)
                result.add(fileUploadData)
            }
        } finally {
            part.dispose()
        }
    }
    return result
}
