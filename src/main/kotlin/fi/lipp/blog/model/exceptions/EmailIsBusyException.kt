package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to register with an email that is already in use.
 */
class EmailIsBusyException : BlogException(
    messageKey = "email_is_busy",
    code = 409
)
