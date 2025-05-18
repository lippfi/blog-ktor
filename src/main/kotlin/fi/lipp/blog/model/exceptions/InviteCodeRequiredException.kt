package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when an invite code is required but not provided.
 */
class InviteCodeRequiredException : BlogException(
    messageKey = "invite_code_required",
    code = 400
)
