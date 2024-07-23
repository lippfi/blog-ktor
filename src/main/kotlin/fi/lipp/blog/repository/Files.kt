package fi.lipp.blog.repository

import fi.lipp.blog.data.FileType
import org.jetbrains.exposed.dao.id.UUIDTable

object Files : UUIDTable() {
    val owner = reference("owner", Users)
    val extension = varchar("extension", 5)
    val fileType = enumerationByName("file_type", 10, FileType::class)
}