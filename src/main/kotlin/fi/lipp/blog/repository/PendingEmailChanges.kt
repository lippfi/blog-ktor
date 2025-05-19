package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object PendingEmailChanges : UUIDTable() {
    val user = reference("user_id", fi.lipp.blog.repository.Users)
    val newEmail = varchar("new_email", 50)
    val issuedAt = datetime("issued_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}