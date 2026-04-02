package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object IgnoreList : UUIDTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val ignoredUser = reference("ignored_user_id", Users, onDelete = ReferenceOption.CASCADE)
    val reason = text("reason").nullable()

    init {
        uniqueIndex("idx_ignore_list", user, ignoredUser)
    }
}