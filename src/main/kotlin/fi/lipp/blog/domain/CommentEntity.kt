package fi.lipp.blog.domain

import fi.lipp.blog.repository.*
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
    var reactionGroupId by Comments.reactionGroup
}

class CommentReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentReactionEntity>(CommentReactions)

    var user by UserEntity referencedOn CommentReactions.user
    var comment by CommentEntity referencedOn CommentReactions.comment
    var reaction by ReactionEntity referencedOn CommentReactions.reaction
    val timestamp by CommentReactions.timestamp
}

class AnonymousCommentReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AnonymousCommentReactionEntity>(AnonymousCommentReactions)

    var ipFingerprint by AnonymousCommentReactions.ipFingerprint
    var comment by CommentEntity referencedOn AnonymousCommentReactions.comment
    var reaction by ReactionEntity referencedOn AnonymousCommentReactions.reaction
    val timestamp by AnonymousCommentReactions.timestamp
}
