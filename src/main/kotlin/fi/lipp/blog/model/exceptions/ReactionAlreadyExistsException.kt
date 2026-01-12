package fi.lipp.blog.model.exceptions

class ReactionAlreadyExistsException : BlogException(
    messageKey = "reaction.already.exists",
    code = 409
)
