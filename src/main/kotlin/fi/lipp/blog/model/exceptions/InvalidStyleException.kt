package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid style is provided.
 */
class InvalidStyleException : BlogException(
    messageKey = "invalid_style",
    code = 400
)
