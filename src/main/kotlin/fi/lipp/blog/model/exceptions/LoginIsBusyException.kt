package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to register with a login that is already in use.
 */
class LoginIsBusyException : BlogException(
    messageKey = "login_is_busy",
    code = 409
)
