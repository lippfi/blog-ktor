package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Dialogs : UUIDTable() {
    val user1 = reference("user1", Users, onDelete = ReferenceOption.CASCADE)
    val user2 = reference("user2", Users, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object Messages : UUIDTable() {
    val dialog = reference("dialog", Dialogs, onDelete = ReferenceOption.CASCADE)
    val sender = reference("sender", Users, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val timestamp = timestamp("timestamp").clientDefault { Clock.System.now() }
    val isRead = bool("is_read").default(false)
    val avatarUri = varchar("avatar_uri", 255).nullable()
}

object HiddenDialogs : UUIDTable() {
    val dialog = reference("dialog", Dialogs, onDelete = ReferenceOption.CASCADE)
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val hiddenAt = timestamp("hidden_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex("unique_hidden_dialog", dialog, user)
    }
}
