package fi.lipp.blog.domain

import fi.lipp.blog.repository.ExternalUsers
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class ExternalUserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ExternalUserEntity>(ExternalUsers)

    var user by UserEntity optionalReferencedOn ExternalUsers.user
    var nickname by ExternalUsers.nickname
}
