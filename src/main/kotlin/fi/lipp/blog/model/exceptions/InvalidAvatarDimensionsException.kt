package fi.lipp.blog.model.exceptions

class InvalidAvatarDimensionsException : BlogException("Avatar dimensions must be 100x100 pixels", 400)