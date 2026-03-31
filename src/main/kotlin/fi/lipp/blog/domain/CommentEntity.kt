package fi.lipp.blog.domain

import fi.lipp.blog.repository.*
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(Comments)

    var post by PostEntity referencedOn Comments.post
    var authorType by Comments.authorType
    var localAuthor by UserEntity optionalReferencedOn Comments.localAuthor
    var externalAuthor by ExternalUserEntity optionalReferencedOn Comments.externalAuthor
    var anonymousAuthor by AnonymousUserEntity optionalReferencedOn Comments.anonymousAuthor

    var avatar by Comments.avatar
    var text by Comments.text

    var parentComment by CommentEntity optionalReferencedOn Comments.parentComment
    val creationTime by Comments.creationTime
    var isPublished by Comments.isPublished

    val postId: EntityID<UUID>
        get() = post.id

    fun getEffectiveAuthor(): UserEntity? {
        return when (authorType) {
            CommentAuthorType.LOCAL -> localAuthor
            CommentAuthorType.EXTERNAL -> externalAuthor?.user
            CommentAuthorType.ANONYMOUS -> null
        }
    }

    fun getAuthorRef(): CommentAuthorRef {
        return when (authorType) {
            CommentAuthorType.LOCAL -> CommentAuthorRef.Local(
                localAuthor ?: error("Comment $id has LOCAL authorType but localAuthor is null")
            )
            CommentAuthorType.EXTERNAL -> CommentAuthorRef.External(
                externalAuthor ?: error("Comment $id has EXTERNAL authorType but externalAuthor is null")
            )
            CommentAuthorType.ANONYMOUS -> CommentAuthorRef.Anonymous(
                anonymousAuthor ?: error("Comment $id has ANONYMOUS authorType but anonymousAuthor is null")
            )
        }
    }

    val authorId: UUID?
        get() = getEffectiveAuthor()?.id?.value
}

sealed interface CommentAuthorRef {
    data class Local(val user: UserEntity) : CommentAuthorRef
    data class External(val externalUser: ExternalUserEntity) : CommentAuthorRef
    data class Anonymous(val anonymousUser: AnonymousUserEntity) : CommentAuthorRef
}

class CommentReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentReactionEntity>(CommentReactions)

    var comment by CommentEntity referencedOn CommentReactions.comment
    var reaction by ReactionEntity referencedOn CommentReactions.reaction
    var user by UserEntity referencedOn CommentReactions.user
    val timestamp by CommentReactions.timestamp
}

class AnonymousCommentReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AnonymousCommentReactionEntity>(AnonymousCommentReactions)

    var ipFingerprint by AnonymousCommentReactions.ipFingerprint
    var comment by CommentEntity referencedOn AnonymousCommentReactions.comment
    var reaction by ReactionEntity referencedOn AnonymousCommentReactions.reaction
    val timestamp by AnonymousCommentReactions.timestamp
}
