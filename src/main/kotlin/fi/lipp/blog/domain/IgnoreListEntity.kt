package fi.lipp.blog.domain

import fi.lipp.blog.repository.IgnoreList
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class IgnoreListEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IgnoreListEntity>(IgnoreList)

    var user by UserEntity referencedOn IgnoreList.user
    var ignoredUser by UserEntity referencedOn IgnoreList.ignoredUser
}