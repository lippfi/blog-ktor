package fi.lipp.blog.model.exceptions

class NotMessageSenderException : RuntimeException("Only the sender can delete the message")