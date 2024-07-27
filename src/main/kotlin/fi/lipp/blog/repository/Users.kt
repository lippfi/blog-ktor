package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Users : LongIdTable() {
    val login = varchar("login", 50).uniqueIndex("idx_user_login")
    val email = varchar("email", 50).uniqueIndex("idx_user_email")
    val password = varchar("password", 200)
    val nickname = varchar("nickname", 50).uniqueIndex("idx_user_nickname")
    val registrationTime = datetime("registration_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val inviteCode = reference("invite_code", InviteCodes, onDelete = ReferenceOption.CASCADE).nullable()
}