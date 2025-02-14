package fi.lipp.blog.model.exceptions

class NicknameIsBusyException : BlogException("Nickname is already taken", 409)
