package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object PasswordResets: UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val issuedAt = timestamp("issued_time").clientDefault { Clock.System.now() }
}
