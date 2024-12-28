package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Comments : UUIDTable() {
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val author = reference("author", Users, onDelete = ReferenceOption.CASCADE)

    val avatar = varchar("avatar", 1024)
    val text = text("text")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}