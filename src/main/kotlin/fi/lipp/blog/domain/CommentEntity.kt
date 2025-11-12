package fi.lipp.blog.domain

import fi.lipp.blog.repository.*
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(Comments)

    val postId by Comments.post
    val authorType by Comments.authorType
    val localAuthor by Comments.localAuthor
    val externalAuthor by Comments.externalAuthor
    val anonymousAuthor by Comments.anonymousAuthor
    val authorId: UUID?
        get() = when (authorType) {
            CommentAuthorType.LOCAL -> localAuthor!!.value
            CommentAuthorType.EXTERNAL -> ExternalUserEntity.findById(externalAuthor!!)!!.user?.value
            CommentAuthorType.ANONYMOUS -> null
        }
    val authorNickname: String
        get() = when (authorType) {
            CommentAuthorType.LOCAL -> UserEntity.findById(localAuthor!!)!!.nickname
            CommentAuthorType.EXTERNAL -> ExternalUserEntity.findById(externalAuthor!!)!!.nickname
            CommentAuthorType.ANONYMOUS -> AnonymousUserEntity.findById(anonymousAuthor!!)!!.nickname
        }
    val authorDiaryLogin: String?
        get() {
            val authorId = authorId ?: return null
            return DiaryEntity.find { Diaries.owner eq authorId }.single().login
        }

    var avatar by Comments.avatar
    var text by Comments.text

    val parentComment by Comments.parentComment
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
