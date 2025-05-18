package fi.lipp.blog.model.exceptions

class DialogAlreadyHiddenException : BlogException(
    messageKey = "dialog_already_hidden",
    code = 409
)
