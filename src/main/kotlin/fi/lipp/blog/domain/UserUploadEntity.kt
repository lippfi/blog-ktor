package fi.lipp.blog.domain

import fi.lipp.blog.repository.UserUploads
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserUploadEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserUploadEntity>(UserUploads)

    var user by UserEntity referencedOn UserUploads.user
    var date by UserUploads.date
    var totalBytes by UserUploads.totalBytes
}