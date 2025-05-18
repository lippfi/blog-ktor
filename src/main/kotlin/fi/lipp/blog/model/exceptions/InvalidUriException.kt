package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid URI is provided.
 */
class InvalidUriException : BlogException(
    messageKey = "invalid_uri",
    code = 400
)
