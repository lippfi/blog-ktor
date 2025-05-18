package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a reaction is not found.
 */
class ReactionNotFoundException : BlogException(
    messageKey = "reaction_not_found",
    code = 404
)

/**
 * Exception thrown when a localization already exists for a language.
 */
class LocalizationAlreadyExistsException : BlogException(
    messageKey = "localization_already_exists",
    code = 409
)
