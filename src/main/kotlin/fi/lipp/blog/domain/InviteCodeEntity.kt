package fi.lipp.blog.domain

import fi.lipp.blog.repository.InviteCodes
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class InviteCodeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InviteCodeEntity>(InviteCodes)

    private val issuedAt by InviteCodes.issuedAt
    val isValid get() = Duration.between(issuedAt.toJavaLocalDateTime(), LocalDateTime.now()).toHours() < 24
}