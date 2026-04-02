package fi.lipp.blog.model.exceptions

class SvgUploadNotAllowedException : BlogException(
    messageKey = "svg_upload_not_allowed",
    code = 403
)
