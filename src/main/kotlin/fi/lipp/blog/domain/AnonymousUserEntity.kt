package fi.lipp.blog.domain

import fi.lipp.blog.repository.AnonymousUsers
import fi.lipp.blog.repository.ExternalUsers
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class AnonymousUserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AnonymousUserEntity>(AnonymousUsers)

    val nickname by AnonymousUsers.nickname
    val email by AnonymousUsers.email
    val ipFingerprint by AnonymousUsers.ipFingerprint
}