package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a file with an invalid extension is uploaded.
 */
class InvalidFileExtension : BlogException(
    messageKey = "invalid_file_extension",
    code = 400
)
