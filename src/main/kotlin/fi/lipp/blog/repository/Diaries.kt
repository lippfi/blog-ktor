package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Diaries : UUIDTable() {
    val name = varchar("name", 40)
    val subtitle = varchar("subtitle", 200)

    val login = varchar("login", 50).uniqueIndex("idx_diary_login")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)
    // style column removed - now managed through DiaryStyles table

    val defaultReadGroup = reference("default_read_group", AccessGroups)
    val defaultCommentGroup = reference("default_comment_group", AccessGroups)
    val defaultReactGroup = reference("default_react_group", AccessGroups)

    val type = enumerationByName<DiaryType>("type", 50)
}

enum class DiaryType {
    PERSONAL,
    GROUP,
}
