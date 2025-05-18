package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a password reset code is invalid or has expired.
 */
class ResetCodeInvalidOrExpiredException : BlogException(
    messageKey = "reset_code_invalid_or_expired",
    code = 400
)
