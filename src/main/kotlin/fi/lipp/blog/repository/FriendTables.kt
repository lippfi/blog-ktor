package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

object FriendRequests : UUIDTable() {
    val fromUser = reference("from_user", Users, onDelete = ReferenceOption.CASCADE)
    val toUser = reference("to_user", Users, onDelete = ReferenceOption.CASCADE)
    val message = varchar("message", 500)
    val label = varchar("label", 50).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("idx_friend_request_unique", fromUser, toUser)
    }
}

object Friends : UUIDTable() {
    val user1 = reference("user1", Users, onDelete = ReferenceOption.CASCADE)
    val user2 = reference("user2", Users, onDelete = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("idx_friendship_unique", user1, user2)
    }
}

object FriendLabels : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val friend = reference("friend", Users, onDelete = ReferenceOption.CASCADE)
    val label = varchar("label", 50).nullable()
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("idx_friend_label_unique", user, friend)
    }
}
