package fi.lipp.blog.model.exceptions

/**
 * Exception thrown when a user exceeds their daily upload limit.
 *
 * @param limit The maximum allowed upload size in bytes
 * @param current The current upload size in bytes
 */
class DailyUploadLimitExceededException(limit: Long, current: Long) : 
    BlogException(
        messageKey = "daily_upload_limit_exceeded",
        code = 400
    )
