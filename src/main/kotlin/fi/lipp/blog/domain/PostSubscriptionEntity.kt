package fi.lipp.blog.domain

import fi.lipp.blog.repository.PostSubscriptions
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class PostSubscriptionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostSubscriptionEntity>(PostSubscriptions)

    var user by UserEntity referencedOn PostSubscriptions.user
    var post by PostEntity referencedOn PostSubscriptions.post
}