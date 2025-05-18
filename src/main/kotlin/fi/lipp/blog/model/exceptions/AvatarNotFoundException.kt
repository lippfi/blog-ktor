package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an avatar is not found.
 */
class AvatarNotFoundException: BlogException(
    messageKey = "avatar_not_found",
    code = 404
)
