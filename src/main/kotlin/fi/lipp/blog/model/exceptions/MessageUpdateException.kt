package fi.lipp.blog.model.exceptions

class MessageUpdateException : RuntimeException("Message can only be updated if it is unread or was sent less than 30 minutes ago")