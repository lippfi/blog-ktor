package fi.lipp.blog.domain

import fi.lipp.blog.repository.CommentDependencies
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

/**
 * Entity representing a dependency between a comment and a user.
 * A comment depends on:
 * 1. Diary owner
 * 2. Post owner (author)
 * 3. Comment author
 * 4. All people on which the parent comment depends (recursive dependency)
 */
class CommentDependencyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentDependencyEntity>(CommentDependencies)

    var comment by CommentEntity referencedOn CommentDependencies.comment
    var user by UserEntity referencedOn CommentDependencies.user
}