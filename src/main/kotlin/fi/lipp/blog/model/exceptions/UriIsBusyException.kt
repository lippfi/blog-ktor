package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a URI is already in use.
 */
class UriIsBusyException : BlogException(
    messageKey = "uri_is_busy",
    code = 409
)
