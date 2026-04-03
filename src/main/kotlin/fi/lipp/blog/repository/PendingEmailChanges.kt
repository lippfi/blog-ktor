package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PendingEmailChanges : UUIDTable() {
    val user = reference("user_id", fi.lipp.blog.repository.Users)
    val newEmail = varchar("new_email", 50)
    val issuedAt = timestamp("issued_time").clientDefault { Clock.System.now() }
}
