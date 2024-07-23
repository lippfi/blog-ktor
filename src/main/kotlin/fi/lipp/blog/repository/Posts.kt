package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime
import java.util.UUID

object Posts : UUIDTable() {
    val uri = varchar("uri", 100).index("idx_post_uri")

    val diary = reference("diary", Diaries).index("idx_post_diary")
    val author = reference("author", Users).index("idx_post_author")

    val avatar = varchar("avatar", 1024)
    val title = text("title")
    val text = text("text")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val isPreface = bool("is_preface")
    val isPrivate = bool("is_private")
    val isEncrypted = bool("is_encrypted")

    val isArchived = bool("is_archived")

    val classes = varchar("classes", 1024)
}