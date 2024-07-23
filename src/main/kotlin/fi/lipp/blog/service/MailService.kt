package fi.lipp.blog.service

interface MailService {
    fun sendEmail(subject: String, text: String, recipient: String)
}
