package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user tries to unfollow another user they are not following.
 */
class NotFollowingException : BlogException(
    messageKey = "not_following",
    code = 400
)
