package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Diaries : LongIdTable() {
    val name = varchar("name", 20)
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)
    val style = reference("style", Files, onDelete = ReferenceOption.CASCADE).nullable()
}