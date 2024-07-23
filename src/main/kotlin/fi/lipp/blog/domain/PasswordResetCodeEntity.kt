package fi.lipp.blog.domain

import fi.lipp.blog.repository.PasswordResets
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class PasswordResetCodeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PasswordResetCodeEntity>(PasswordResets)

    val userId by PasswordResets.user

    val resetIssuedAt by PasswordResets.issuedAt
    val isValid get() =  Duration.between(resetIssuedAt.toJavaLocalDateTime(), LocalDateTime.now()).toMinutes() < 30
}