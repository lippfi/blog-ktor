package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to confirm registration with an invalid or expired confirmation code.
 */
class ConfirmationCodeInvalidOrExpiredException : BlogException(
    messageKey = "confirmation_code_invalid_or_expired",
    code = 400
)