package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an avatar with an invalid file extension is uploaded.
 */
class InvalidAvatarExtensionException : BlogException(
    messageKey = "invalid_avatar_extension",
    code = 400
)
