package fi.lipp.blog.stubs

import fi.lipp.blog.service.MailService

class MailServiceStub: MailService {
    override fun sendEmail(subject: String, text: String, recipient: String) {
        TODO("Not yet implemented")
    }
}