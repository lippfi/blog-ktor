package fi.lipp.blog.domain

import fi.lipp.blog.repository.HiddenFromFeed
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class HiddenFromFeedEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<HiddenFromFeedEntity>(HiddenFromFeed)

    var user by UserEntity referencedOn HiddenFromFeed.user
    var hiddenUser by UserEntity referencedOn HiddenFromFeed.hiddenUser
}
