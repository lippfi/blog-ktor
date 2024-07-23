package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.Table

object UserAvatars : Table() {
    val ordinal = integer("ordinal")
    val user = reference("user", Users)
    val avatar = reference("file", Files)
    override val primaryKey = PrimaryKey(user, avatar)
}
