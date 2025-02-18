package fi.lipp.blog.domain

import fi.lipp.blog.repository.Messages
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class MessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageEntity>(Messages)

    var dialog by DialogEntity referencedOn Messages.dialog
    var sender by UserEntity referencedOn Messages.sender
    var content by Messages.content
    var timestamp by Messages.timestamp
    var isRead by Messages.isRead
    var avatarUri by Messages.avatarUri
}
