package fi.lipp.blog.domain

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.repository.Files
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntity>(Files)

    val name by Files.name
    val owner by Files.owner
    val fileType by Files.fileType

    fun toBlogFile(): BlogFile {
        return BlogFile(id.value, owner.value, name, fileType)
    }
}