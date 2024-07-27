package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UserAvatars : Table() {
    val ordinal = integer("ordinal")
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val avatar = reference("file", Files, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(user, avatar)
}
