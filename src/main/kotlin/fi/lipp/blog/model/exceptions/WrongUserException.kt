package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an operation is attempted with the wrong user.
 */
class WrongUserException : BlogException(
    messageKey = "wrong_user",
    code = 404
)
