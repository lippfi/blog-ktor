package fi.lipp.blog.domain

import fi.lipp.blog.repository.HiddenDialogs
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class HiddenDialogEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<HiddenDialogEntity>(HiddenDialogs)

    var dialog by DialogEntity referencedOn HiddenDialogs.dialog
    var user by UserEntity referencedOn HiddenDialogs.user
    var hiddenAt by HiddenDialogs.hiddenAt
}
