package fi.lipp.blog.domain

import fi.lipp.blog.repository.Comments
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(Comments)

    val authorId by Comments.author

    var avatar by Comments.avatar
    var text by Comments.text

    val creationTime by Comments.creationTime
}