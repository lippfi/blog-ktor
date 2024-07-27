package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Posts : UUIDTable() {
    val uri = varchar("uri", 100).index("idx_post_uri")

    val diary = reference("diary", Diaries, onDelete = ReferenceOption.CASCADE).index("idx_post_diary")
    val author = reference("author", Users, onDelete = ReferenceOption.CASCADE).index("idx_post_author")

    val avatar = varchar("avatar", 1024)
    val title = text("title")
    val text = text("text")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val isPreface = bool("is_preface")
    val isEncrypted = bool("is_encrypted")

    val isArchived = bool("is_archived")

    val classes = varchar("classes", 1024)

    val readGroup = reference("read_group", AccessGroups, onDelete = ReferenceOption.CASCADE)
    val commentGroup = reference("comment_group", AccessGroups, onDelete = ReferenceOption.CASCADE)
}