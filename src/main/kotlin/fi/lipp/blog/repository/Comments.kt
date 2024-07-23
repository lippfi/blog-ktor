package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime
import java.util.UUID

object Comments : UUIDTable() {
    // TODO can it be a reference to URI and not ID? URI does not change while ID keeps changing
    val post = reference("post", Posts)
    val author = reference("author", Users)

    val avatar = varchar("avatar", 1024)
    val text = text("text")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}