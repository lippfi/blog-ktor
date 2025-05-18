package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid access group is specified.
 */
class InvalidAccessGroupException : BlogException(
    messageKey = "invalid_access_group",
    code = 403
)
