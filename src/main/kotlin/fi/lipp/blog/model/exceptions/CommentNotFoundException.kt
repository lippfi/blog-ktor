package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a comment is not found.
 */
class CommentNotFoundException : BlogException(
    messageKey = "comment_not_found",
    code = 404
)
