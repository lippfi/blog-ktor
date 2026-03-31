package fi.lipp.blog.service

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.domain.*
import org.jetbrains.exposed.sql.Transaction

fun CommentEntity.toComment(
    transaction: Transaction,
    viewer: Viewer,
    accessGroupService: AccessGroupService,
    reactionService: ReactionService
): CommentDto.View {
    val commentedPost = post
    val commentedDiary = commentedPost.diary
    val commentedDiaryOwnerId = commentedDiary.owner.value
    val commentReactionGroupId = commentedPost.commentReactionGroupId.value

    val authorView = toAuthorView()
    val viewerUserId = (viewer as? Viewer.Registered)?.userId
    val isSelf = viewerUserId != null && viewerUserId == getEffectiveAuthor()?.id?.value

    val canReact = isSelf || accessGroupService.inGroup(viewer, commentReactionGroupId, commentedDiaryOwnerId)
    val inReplyTo = collectReplyTo(parentComment)

    return CommentDto.View(
        id = id.value,
        authorLogin = authorView.login,
        authorNickname = authorView.nickname,
        diaryLogin = commentedDiary.login,
        postUri = commentedPost.uri,
        avatar = avatar,
        text = text,
        creationTime = creationTime,
        isReactable = canReact,
        reactions = reactionService.getCommentReactions(id.value),
        reactionGroupId = commentReactionGroupId,
        inReplyTo = inReplyTo,
        isPublished = isPublished
    )
}

fun collectReplyTo(parentComment: CommentEntity?): CommentDto.ReplyView? {
    if (parentComment == null) return null

    val authorView = parentComment.toAuthorView()

    return CommentDto.ReplyView(
        id = parentComment.id.value,
        login = authorView.login,
        nickname = authorView.nickname
    )
}

private data class CommentAuthorView(
    val login: String?,
    val nickname: String,
)

private fun CommentEntity.toAuthorView(): CommentAuthorView {
    return when (val author = getAuthorRef()) {
        is CommentAuthorRef.Local -> {
            val diaryLogin = DiaryEntity
                .find { fi.lipp.blog.repository.Diaries.owner eq author.user.id }
                .singleOrNull()
                ?.login

            CommentAuthorView(
                login = diaryLogin,
                nickname = author.user.nickname,
            )
        }
        is CommentAuthorRef.External -> CommentAuthorView(
            login = author.externalUser.user?.let { linkedUser ->
                DiaryEntity.find { fi.lipp.blog.repository.Diaries.owner eq linkedUser.id }.singleOrNull()?.login
            },
            nickname = author.externalUser.user?.nickname ?: author.externalUser.nickname,
        )
        is CommentAuthorRef.Anonymous -> CommentAuthorView(
            login = null,
            nickname = author.anonymousUser.nickname ?: "anonymous",
        )
    }
}
