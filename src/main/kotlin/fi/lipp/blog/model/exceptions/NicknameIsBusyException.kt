package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to register with a nickname that is already in use.
 */
class NicknameIsBusyException : BlogException(
    messageKey = "nickname_is_busy",
    code = 409
)
