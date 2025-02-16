package fi.lipp.blog.model.exceptions

class InvalidReactionImageException : Exception("Invalid reaction image file extension. Only .jpg, .jpeg, .png, and .gif are allowed.")