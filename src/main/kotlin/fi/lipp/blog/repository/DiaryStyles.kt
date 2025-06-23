package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object DiaryStyles : UUIDTable() {
    val name = varchar("name", 50)
    val description = text("description").nullable()
    val styleFile = reference("style_file", Files, onDelete = ReferenceOption.CASCADE)
}
