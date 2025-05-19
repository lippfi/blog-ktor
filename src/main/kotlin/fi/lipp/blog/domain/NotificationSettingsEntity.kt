package fi.lipp.blog.domain

import fi.lipp.blog.repository.NotificationSettings
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class NotificationSettingsEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NotificationSettingsEntity>(NotificationSettings)

    var user by UserEntity referencedOn NotificationSettings.user
    var notifyAboutComments by NotificationSettings.notifyAboutComments
    var notifyAboutReplies by NotificationSettings.notifyAboutReplies
    var notifyAboutPostReactions by NotificationSettings.notifyAboutPostReactions
    var notifyAboutCommentReactions by NotificationSettings.notifyAboutCommentReactions
    var notifyAboutPrivateMessages by NotificationSettings.notifyAboutPrivateMessages
    var notifyAboutMentions by NotificationSettings.notifyAboutMentions
}