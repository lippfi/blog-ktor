package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object PasswordResets: UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val issuedAt = datetime("issued_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}