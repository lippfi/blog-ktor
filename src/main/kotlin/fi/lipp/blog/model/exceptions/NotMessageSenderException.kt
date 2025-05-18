package fi.lipp.blog.model.exceptions

class NotMessageSenderException : BlogException(
    messageKey = "not_message_sender",
    code = 500
)
