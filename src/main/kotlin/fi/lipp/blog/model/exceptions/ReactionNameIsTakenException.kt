package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a reaction name is already taken.
 */
class ReactionNameIsTakenException : BlogException(
    messageKey = "reaction_name_is_taken",
    code = 400
)
