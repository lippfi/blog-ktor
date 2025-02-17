package fi.lipp.blog.domain

import fi.lipp.blog.data.Language
import fi.lipp.blog.data.NSFWPolicy
import fi.lipp.blog.data.Sex
import fi.lipp.blog.data.StorageQuota
import fi.lipp.blog.repository.Users
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(Users)

    var email: String by Users.email
    var password: String by Users.password
    var nickname: String by Users.nickname
    var registrationTime: LocalDateTime by Users.registrationTime

    var sex: Sex by Users.sex
    var nsfw: NSFWPolicy by Users.nsfw
    var timezone: String by Users.timezone
    var language: Language by Users.language
    var birthdate: LocalDate? by Users.birthdate

    // Notification settings
    var notifyAboutComments: Boolean by Users.notifyAboutComments
    var notifyAboutReplies: Boolean by Users.notifyAboutReplies
    var notifyAboutPostReactions: Boolean by Users.notifyAboutPostReactions
    var notifyAboutCommentReactions: Boolean by Users.notifyAboutCommentReactions
    var notifyAboutPrivateMessages: Boolean by Users.notifyAboutPrivateMessages
    var notifyAboutMentions: Boolean by Users.notifyAboutMentions
    var storageQuota: StorageQuota by Users.storageQuota
    var primaryAvatar: EntityID<UUID>? by Users.primaryAvatar
}
