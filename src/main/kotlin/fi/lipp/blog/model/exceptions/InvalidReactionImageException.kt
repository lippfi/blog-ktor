package fi.lipp.blog.model.exceptions

class InvalidReactionImageException : BlogException(
    messageKey = "invalid_reaction_image",
    code = 400,
)