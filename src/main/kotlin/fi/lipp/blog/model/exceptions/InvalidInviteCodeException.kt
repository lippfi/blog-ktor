package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid invite code is provided.
 */
class InvalidInviteCodeException : BlogException(
    messageKey = "invalid_invite_code",
    code = 400
)
