package fi.lipp.blog.model.exceptions

class InvalidAvatarSizeException : BlogException("Avatar file size must be less than 1MB", 400)