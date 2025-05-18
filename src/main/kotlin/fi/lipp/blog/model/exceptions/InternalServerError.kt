package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an unexpected internal server error occurs.
 */
class InternalServerError: BlogException(
    messageKey = "internal_server_error",
    code = 500
)
