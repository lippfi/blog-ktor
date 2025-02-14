package fi.lipp.blog.model.exceptions

class LoginIsBusyException : BlogException("Login is already taken", 409)
