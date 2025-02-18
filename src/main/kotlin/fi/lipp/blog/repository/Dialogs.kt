package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Dialogs : UUIDTable() {
    val user1 = reference("user1", Users, onDelete = ReferenceOption.CASCADE)
    val user2 = reference("user2", Users, onDelete = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}

object Messages : UUIDTable() {
    val dialog = reference("dialog", Dialogs, onDelete = ReferenceOption.CASCADE)
    val sender = reference("sender", Users, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val isRead = bool("is_read").default(false)
    val avatarUri = varchar("avatar_uri", 255).nullable()
}

object HiddenDialogs : UUIDTable() {
    val dialog = reference("dialog", Dialogs, onDelete = ReferenceOption.CASCADE)
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val hiddenAt = datetime("hidden_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("unique_hidden_dialog", dialog, user)
    }
}
