package fi.lipp.blog.repository

import fi.lipp.blog.data.Language
import fi.lipp.blog.data.NSFWPolicy
import fi.lipp.blog.data.Sex
import fi.lipp.blog.data.StorageQuota
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Users : UUIDTable() {
    val email = varchar("email", 50).uniqueIndex("idx_user_email")
    val password = varchar("password", 200)
    val nickname = varchar("nickname", 50).uniqueIndex("idx_user_nickname")
    val registrationTime = datetime("registration_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val inviteCode = reference("invite_code", InviteCodes, onDelete = ReferenceOption.CASCADE).nullable()

    val sex = enumerationByName("sex", 20, Sex::class)
    val timezone = varchar("timezone", 40)
    val language = enumerationByName("language", 20, Language::class)
    val nsfw = enumerationByName("nsfw", 20, NSFWPolicy::class)
    val birthdate = date("birthdate").nullable()

    // Storage quota and avatar settings
    val storageQuota = enumerationByName("storage_quota", 20, StorageQuota::class).default(StorageQuota.BASIC)
    val primaryAvatar = reference("primary_avatar", Files, onDelete = ReferenceOption.SET_NULL).nullable()
}
