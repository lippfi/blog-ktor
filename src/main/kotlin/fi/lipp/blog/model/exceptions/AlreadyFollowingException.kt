package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to follow another user they are already following.
 */
class AlreadyFollowingException : BlogException(
    messageKey = "already_following",
    code = 400
)
