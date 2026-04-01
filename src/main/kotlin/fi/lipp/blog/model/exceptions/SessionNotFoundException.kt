package fi.lipp.blog.model.exceptions

class SessionNotFoundException : BlogException(
    messageKey = "session_not_found",
    code = 401
)
