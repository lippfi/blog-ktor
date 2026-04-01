package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object HiddenFromFeed : UUIDTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val hiddenUser = reference("hidden_user_id", Users, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex("idx_hidden_from_feed", user, hiddenUser)
    }
}
