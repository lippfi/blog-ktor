package fi.lipp.blog.domain

import fi.lipp.blog.data.Comment
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.Comments
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(Comments)

    val postId by Comments.post
    val authorId by Comments.author

    var avatar by Comments.avatar
    var text by Comments.text
    val creationTime by Comments.creationTime

    fun toComment(): Comment {
        val author = UserEntity.findById(authorId) ?: throw InternalServerError()
        return Comment(
            id = id.value,
            postId = postId.value,
            authorLogin = author.login,
            authorNickname = author.nickname,
            avatar = avatar,
            text = text,
            creationTime = creationTime,
        )
    }
}