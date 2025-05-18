package fi.lipp.blog.model.exceptions

class DialogNotFoundException : BlogException(messageKey = "dialog_not_found", code = 404)
class NotDialogParticipantException : BlogException(messageKey = "not_dialog_participant", code = 400)