package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid email address is provided.
 */
class InvalidEmailException : BlogException(
    messageKey = "invalid_email",
    code = 400
)
