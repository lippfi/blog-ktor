package fi.lipp.blog.service

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.repository.CommentAuthorType
import fi.lipp.blog.repository.Diaries
import org.jetbrains.exposed.sql.Transaction
import java.util.*

fun CommentEntity.toComment(
    transaction: Transaction,
    viewer: Viewer,
    accessGroupService: AccessGroupService,
    reactionService: ReactionService
): CommentDto.View {
    val commentedPost = PostEntity.findById(postId)!!
    val commentedDiary = DiaryEntity.findById(commentedPost.diaryId)!!
    val commentedDiaryOwnerId = commentedDiary.owner.value

    val (authorLogin: String?, authorNickname: String) = when (authorType) {
        CommentAuthorType.LOCAL -> {
            val u = UserEntity.findById(localAuthor!!)!!
            val d = DiaryEntity.find { Diaries.owner eq u.id }.single()
            d.login to u.nickname
        }
        CommentAuthorType.EXTERNAL -> {
            val ext = ExternalUserEntity.findById(externalAuthor!!)!!
            val linked = ext.user?.let { UserEntity.findById(it) }
            if (linked != null) {
                val d = DiaryEntity.find { Diaries.owner eq linked.id }.single()
                d.login to linked.nickname
            } else {
                null to ext.nickname
            }
        }
        CommentAuthorType.ANONYMOUS -> {
            val anon = AnonymousUserEntity.findById(anonymousAuthor!!)!!
            null to anon.nickname
        }
    }

    val canReact = accessGroupService.inGroup(viewer, reactionGroupId.value, commentedDiaryOwnerId)
    val inReplyTo = collectReplyTo(parentComment?.value)
    return CommentDto.View(
        id = id.value,
        authorLogin = authorLogin,
        authorNickname = authorNickname,
        diaryLogin = commentedDiary.login,
        postUri = commentedPost.uri,
        avatar = avatar,
        text = text,
        creationTime = creationTime,
        isReactable = canReact,
        reactions = reactionService.getCommentReactions(id.value),
        reactionGroupId = reactionGroupId.value,
        inReplyTo = inReplyTo
    )
}

fun collectReplyTo(parentCommentId: UUID?): CommentDto.ReplyView? {
    if (parentCommentId == null) return null
    val parentComment = CommentEntity.findById(parentCommentId) ?: return null

    return CommentDto.ReplyView(
        id = parentComment.id.value,
        login = parentComment.authorDiaryLogin,
        nickname = parentComment.authorNickname
    )
}
