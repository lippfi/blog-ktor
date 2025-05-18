package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a post is not found.
 */
class PostNotFoundException : BlogException(
    messageKey = "post_not_found",
    code = 404
)
