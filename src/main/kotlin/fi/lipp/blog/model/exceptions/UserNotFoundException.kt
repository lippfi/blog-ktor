package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user is not found.
 */
class UserNotFoundException : BlogException(
    messageKey = "user_not_found",
    code = 404
)
