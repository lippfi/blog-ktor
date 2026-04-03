package fi.lipp.blog.model.exceptions

class InviteCodeGenerationNotAllowedException : BlogException(
    messageKey = "invite_code_generation_not_allowed",
    code = 403
)
