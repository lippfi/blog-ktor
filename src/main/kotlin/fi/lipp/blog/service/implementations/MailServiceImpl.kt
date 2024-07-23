package fi.lipp.blog.service.implementations

import fi.lipp.blog.service.ApplicationProperties
import fi.lipp.blog.service.MailService
import io.ktor.server.application.*
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

class MailServiceImpl(val properties: ApplicationProperties) : MailService {
    private val props = Properties().apply {
        this["mail.smtp.host"] = properties.emailHost
        this["mail.smtp.port"] = properties.emailHost
        this["mail.smtp.auth"] = "true"
        this["mail.smtp.starttls.enable"] = "true"
    }
    private var authenticator: Authenticator = object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(properties.emailAddress, properties.emailPassword)
        }
    }
    private val session: Session = Session.getInstance(props, authenticator)

    override fun sendEmail(subject: String, text: String, recipient: String) {
        val message = MimeMessage(session)
        message.setFrom(properties.emailAddress)
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
        message.setSubject(subject)
        message.setText(text)
        Transport.send(message)
    }
}