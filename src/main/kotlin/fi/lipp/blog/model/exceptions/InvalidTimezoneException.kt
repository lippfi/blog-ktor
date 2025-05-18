package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid timezone is specified.
 */
class InvalidTimezoneException : BlogException(
    messageKey = "invalid_timezone",
    code = 400
)
