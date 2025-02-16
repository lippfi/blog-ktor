package fi.lipp.blog.model.exceptions

class ReactionNotFoundException : BlogException(
    "Reaction not found",
    404
)

class LocalizationAlreadyExistsException : BlogException(
    "Localization already exists for this language",
    409
)
