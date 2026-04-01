package fi.lipp.blog.domain

import fi.lipp.blog.repository.PostDependencies
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

/**
 * Entity representing a dependency between a post and a user.
 * A post depends on its author and, for reposts, all users from the original post/comment dependencies.
 */
class PostDependencyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostDependencyEntity>(PostDependencies)

    var post by PostEntity referencedOn PostDependencies.post
    var user by UserEntity referencedOn PostDependencies.user
}
