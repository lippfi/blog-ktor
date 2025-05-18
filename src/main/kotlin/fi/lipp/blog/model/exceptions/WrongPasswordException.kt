package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user provides an incorrect password.
 */
class WrongPasswordException : BlogException(
    messageKey = "wrong_password",
    code = 401
)
