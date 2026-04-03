package fi.lipp.blog.domain

import fi.lipp.blog.repository.InviteCodes
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*
import kotlin.time.Duration.Companion.hours

class InviteCodeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InviteCodeEntity>(InviteCodes)

    private val issuedAt by InviteCodes.issuedAt
    val isValid get() = (Clock.System.now() - issuedAt) < 24.hours
}
