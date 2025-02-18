package fi.lipp.blog.domain

import fi.lipp.blog.repository.Dialogs
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class DialogEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DialogEntity>(Dialogs)

    var user1 by UserEntity referencedOn Dialogs.user1
    var user2 by UserEntity referencedOn Dialogs.user2
    var createdAt by Dialogs.createdAt
    var updatedAt by Dialogs.updatedAt
}
