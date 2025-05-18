package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invalid parent comment is specified.
 */
class InvalidParentComment : BlogException(
    messageKey = "invalid_parent_comment",
    code = 400
)
