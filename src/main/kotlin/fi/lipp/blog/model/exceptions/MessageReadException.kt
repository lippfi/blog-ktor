package fi.lipp.blog.model.exceptions

class MessageReadException : BlogException(
    messageKey = "message_read_error",
    code = 500
)
