package fi.lipp.blog.model.exceptions

class EmailIsBusyException : BlogException("Email is already in use", 409)
