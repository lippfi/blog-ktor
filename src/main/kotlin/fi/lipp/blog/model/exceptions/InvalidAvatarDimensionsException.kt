package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an avatar with invalid dimensions is uploaded.
 */
class InvalidAvatarDimensionsException : BlogException(
    messageKey = "invalid_avatar_dimensions",
    code = 400
)
