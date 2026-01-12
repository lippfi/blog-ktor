package fi.lipp.blog.repository

import fi.lipp.blog.data.FileType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object Files : UUIDTable("files") {
    val name = varchar("name", 255)
    val owner = reference("owner", Users, onDelete = ReferenceOption.CASCADE)

    val fileType = enumerationByName("file_type", 16, FileType::class)

    val mimeType = varchar("mime_type", 80)
    val hash = varchar("hash", 64).nullable().index("idx_files_hash")
    val ext = varchar("ext", 10).nullable()

    val storageKey = varchar("storage_key", 1024)

    init {
        uniqueIndex("idx_files_type_name_unique", fileType, name)
    }
}