package fi.lipp.blog.model.exceptions

class ResetCodeInvalidOrExpiredException : BlogException("Reset code is invalid or expired", 400)