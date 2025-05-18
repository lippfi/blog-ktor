package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user attempts to access a resource without proper authorization.
 */
class UnauthorizedException: BlogException(
    messageKey = "unauthorized",
    code = 401
)
