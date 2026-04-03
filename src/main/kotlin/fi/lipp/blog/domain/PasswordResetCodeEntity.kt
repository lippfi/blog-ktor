package fi.lipp.blog.domain

import fi.lipp.blog.repository.PasswordResets
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class PasswordResetCodeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PasswordResetCodeEntity>(PasswordResets)

    val userId by PasswordResets.user

    val resetIssuedAt by PasswordResets.issuedAt
    val isValid get() = (Clock.System.now() - resetIssuedAt) < 30.minutes
}
