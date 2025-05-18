package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an avatar with a file size larger than the allowed limit is uploaded.
 */
class InvalidAvatarSizeException : BlogException(
    messageKey = "invalid_avatar_size",
    code = 400
)
