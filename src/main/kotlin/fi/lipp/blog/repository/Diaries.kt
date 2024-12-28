package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Diaries : UUIDTable() {
    val name = varchar("name", 20)
    val login = varchar("login", 50).uniqueIndex("idx_diary_login")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)
    val style = reference("style", Files, onDelete = ReferenceOption.CASCADE).nullable()
    
    val type = enumerationByName<DiaryType>("type", 50)
}

enum class DiaryType {
    PERSONAL,
    GROUP,
}