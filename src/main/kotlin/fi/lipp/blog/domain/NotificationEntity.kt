package fi.lipp.blog.domain

import fi.lipp.blog.repository.*
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime
import java.util.UUID

object Notifications : UUIDTable() {
    val type = enumerationByName("type", 32, NotificationType::class)
    val sender = reference("sender", Users)
    val recipient = reference("recipient", Users)
    val relatedPost = reference("related_post", Posts).nullable()
    val relatedComment = reference("related_comment", Comments).nullable()
    val relatedReaction = reference("related_reaction", Reactions).nullable()
    val relatedRequest = reference("related_request", FriendRequests).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}

class NotificationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NotificationEntity>(Notifications)

    var type by Notifications.type
    var sender by UserEntity referencedOn Notifications.sender
    var recipient by UserEntity referencedOn Notifications.recipient
    var relatedPost by PostEntity.optionalReferencedOn(Notifications.relatedPost)
    var relatedComment by CommentEntity.optionalReferencedOn(Notifications.relatedComment)
    var relatedReaction by ReactionEntity.optionalReferencedOn(Notifications.relatedReaction)
    var relatedRequest by FriendRequestEntity.optionalReferencedOn(Notifications.relatedRequest)
    var isRead by Notifications.isRead
    var createdAt by Notifications.createdAt
}
