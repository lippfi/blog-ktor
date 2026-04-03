package fi.lipp.blog.domain

import fi.lipp.blog.data.Language
import fi.lipp.blog.repository.PendingRegistrations
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class PendingRegistrationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PendingRegistrationEntity>(PendingRegistrations)

    var email by PendingRegistrations.email
    var password by PendingRegistrations.password
    var login by PendingRegistrations.login
    var nickname by PendingRegistrations.nickname
    var timezone by PendingRegistrations.timezone
    var language by PendingRegistrations.language
    var inviteCode by PendingRegistrations.inviteCode
    var issuedAt by PendingRegistrations.issuedAt

    // Registration is valid for 24 hours
    val isValid get() = (Clock.System.now() - issuedAt) < 24.hours
}
