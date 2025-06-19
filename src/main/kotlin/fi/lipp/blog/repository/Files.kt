package fi.lipp.blog.repository

import fi.lipp.blog.data.FileType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object Files : UUIDTable() {
    val name = varchar("name", 255).uniqueIndex("idx_file_name")
    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)
    val fileType = enumerationByName("file_type", 10, FileType::class)
}