package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a diary is not found.
 */
class DiaryNotFoundException : BlogException(
    messageKey = "diary_not_found",
    code = 409
)
