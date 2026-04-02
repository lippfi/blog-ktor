package fi.lipp.blog.model.exceptions

import fi.lipp.blog.data.FileType

class FileTooLargeException(
    val fileType: FileType,
    val maxBytes: Int
) : BlogException(
    messageKey = "file_too_large",
    code = 400
)
