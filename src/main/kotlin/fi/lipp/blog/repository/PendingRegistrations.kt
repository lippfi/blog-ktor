package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object PendingRegistrations : UUIDTable() {
    val email = varchar("email", 50).uniqueIndex("idx_pending_email")
    val password = varchar("password", 200)
    val login = varchar("login", 50).uniqueIndex("idx_pending_login")
    val nickname = varchar("nickname", 50).uniqueIndex("idx_pending_nickname")
    val timezone = varchar("timezone", 40)
    val language = enumerationByName("language", 20, fi.lipp.blog.data.Language::class)
    val inviteCode = reference("invite_code", InviteCodes, onDelete = org.jetbrains.exposed.sql.ReferenceOption.CASCADE).nullable()
    val issuedAt = datetime("issued_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}