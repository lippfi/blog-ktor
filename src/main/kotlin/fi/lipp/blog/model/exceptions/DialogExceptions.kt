package fi.lipp.blog.model.exceptions

class DialogNotFoundException : Exception("Dialog not found")
class NotDialogParticipantException : Exception("User is not a participant of this dialog")