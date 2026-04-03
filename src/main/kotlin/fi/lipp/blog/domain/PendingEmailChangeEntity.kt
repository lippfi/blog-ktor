package fi.lipp.blog.domain

import fi.lipp.blog.repository.PendingEmailChanges
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class PendingEmailChangeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PendingEmailChangeEntity>(PendingEmailChanges)

    var user by UserEntity referencedOn PendingEmailChanges.user
    var newEmail by PendingEmailChanges.newEmail
    var issuedAt by PendingEmailChanges.issuedAt

    // Email change confirmation is valid for 24 hours
    val isValid get() = (Clock.System.now() - issuedAt) < 24.hours
}
