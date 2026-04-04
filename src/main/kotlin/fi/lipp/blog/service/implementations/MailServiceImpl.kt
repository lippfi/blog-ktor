package fi.lipp.blog.service.implementations

import com.resend.Resend
import com.resend.services.emails.model.CreateEmailOptions
import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.MailService

class MailServiceImpl(properties: ApplicationProperties) : MailService {
    val resend = Resend(properties.resendAPIKey)
    val senderEmail = properties.senderEmail

    override fun sendEmail(subject: String, text: String, recipient: String) {
        val sendEmailRequest = CreateEmailOptions.builder()
            .from(senderEmail)
            .to(recipient)
            .subject(subject)
            .html(text)
            .build();

        resend.emails().send(sendEmailRequest);
    }
}