package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid timezone is specified.
 */
class InvalidAvatarUriException : BlogException(
    messageKey = "invalid_avatar_uri",
    code = 400
)
