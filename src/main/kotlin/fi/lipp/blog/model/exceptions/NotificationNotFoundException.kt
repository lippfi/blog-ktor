package fi.lipp.blog.model.exceptions

class NotificationNotFoundException : BlogException(
    messageKey = "notification_not_found",
    code = 404,
)