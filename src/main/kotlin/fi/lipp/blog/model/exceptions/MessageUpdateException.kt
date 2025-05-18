package fi.lipp.blog.model.exceptions

class MessageUpdateException : BlogException(
    messageKey = "message_cant_be_updated",
    code = 500,
)