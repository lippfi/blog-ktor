package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a reaction pack is not found.
 */
class ReactionPackNotFoundException : BlogException(
    messageKey = "reaction_pack_not_found",
    code = 404
)
