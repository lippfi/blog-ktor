package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import java.util.UUID

internal class CommentViewLoader(
    private val accessGroupService: AccessGroupService,
    private val reactionLoader: ReactionLoader,
) {
    private val commentLocalAuthor = Users.alias("comment_local_author")
    private val commentLocalAuthorDiary = Diaries.alias("comment_local_author_diary")
    private val commentExternalAuthor = ExternalUsers.alias("comment_external_author")
    private val commentExternalLinkedUser = Users.alias("comment_external_linked_user")
    private val commentExternalLinkedUserDiary = Diaries.alias("comment_external_linked_user_diary")
    private val commentAnonymousAuthor = AnonymousUsers.alias("comment_anonymous_author")
    private val replyExtLinkUser = Users.alias("reply_ext_link_user")
    private val replyExtLinkDiary = Diaries.alias("reply_ext_link_diary")
    private val postDiaryForComment = Diaries.alias("comment_post_diary")

    private data class ReplyMeta(val login: String?, val nickname: String)

    fun loadCommentsForPosts(transaction: Transaction, viewer: Viewer, postIds: Set<UUID>): Map<UUID, List<CommentDto.View>> {
        if (postIds.isEmpty()) return emptyMap()

        val rows = Comments
            .leftJoin(commentLocalAuthor, { Comments.localAuthor }, { commentLocalAuthor[Users.id] })
            .leftJoin(commentLocalAuthorDiary, { commentLocalAuthor[Users.id] }, { commentLocalAuthorDiary[Diaries.owner] })
            .leftJoin(commentExternalAuthor, { Comments.externalAuthor }, { commentExternalAuthor[ExternalUsers.id] })
            .leftJoin(commentExternalLinkedUser, { commentExternalAuthor[ExternalUsers.user] }, { commentExternalLinkedUser[Users.id] })
            .leftJoin(commentExternalLinkedUserDiary, { commentExternalLinkedUser[Users.id] }, { commentExternalLinkedUserDiary[Diaries.owner] })
            .leftJoin(commentAnonymousAuthor, { Comments.anonymousAuthor }, { commentAnonymousAuthor[AnonymousUsers.id] })
            .innerJoin(Posts, { Comments.post }, { Posts.id })
            .innerJoin(postDiaryForComment, { Posts.diary }, { postDiaryForComment[Diaries.id] })
            .slice(
                Comments.id, Comments.post, Comments.authorType, Comments.localAuthor, Comments.externalAuthor, Comments.anonymousAuthor,
                Comments.avatar, Comments.text, Comments.creationTime, Comments.parentComment,
                Posts.uri, Posts.commentReactionGroup,
                postDiaryForComment[Diaries.login], postDiaryForComment[Diaries.owner],
                commentLocalAuthor[Users.id], commentLocalAuthor[Users.nickname], commentLocalAuthorDiary[Diaries.login],
                commentExternalAuthor[ExternalUsers.id], commentExternalAuthor[ExternalUsers.user], commentExternalAuthor[ExternalUsers.nickname],
                commentExternalLinkedUser[Users.id], commentExternalLinkedUser[Users.nickname], commentExternalLinkedUserDiary[Diaries.login],
                commentAnonymousAuthor[AnonymousUsers.id], commentAnonymousAuthor[AnonymousUsers.nickname]
            )
            .select { Comments.post inList postIds.toList() }
            .orderBy(Comments.creationTime to SortOrder.ASC)
            .toList()

        val viewerUserId = (viewer as? Viewer.Registered)?.userId
        val canReactByPair = rows
            .mapNotNull { row ->
                val localAuthorId = row[Comments.localAuthor]?.value
                val isSelf = viewerUserId != null && localAuthorId != null && viewerUserId == localAuthorId
                if (isSelf) null else row[Posts.commentReactionGroup].value to row[postDiaryForComment[Diaries.owner]].value
            }
            .toSet()
            .associateWith { (groupId, ownerId) -> accessGroupService.inGroup(viewer, groupId, ownerId) }

        val parentIds = rows.mapNotNull { it[Comments.parentComment]?.value }.toSet()
        val replyMeta = loadReplyMeta(parentIds)
        val commentIds = rows.map { it[Comments.id].value }.toSet()
        val reactionsByComment = reactionLoader.loadCommentReactions(transaction, commentIds)

        val byPost = linkedMapOf<UUID, MutableList<CommentDto.View>>()
        rows.forEach { row ->
            val postId = row[Comments.post].value
            val diaryLogin = row[postDiaryForComment[Diaries.login]]
            val postUri = row[Posts.uri]
            val diaryOwnerId = row[postDiaryForComment[Diaries.owner]].value
            val commentReactionGroupId = row[Posts.commentReactionGroup].value

            val (authorLogin, authorNickname, isSelf) = when (row[Comments.authorType]) {
                CommentAuthorType.LOCAL -> {
                    val uid = row[commentLocalAuthor[Users.id]]?.value
                    Triple(
                        row[commentLocalAuthorDiary[Diaries.login]],
                        row[commentLocalAuthor[Users.nickname]],
                        viewerUserId != null && uid != null && viewerUserId == uid
                    )
                }
                CommentAuthorType.EXTERNAL -> {
                    val linkedUid = row[commentExternalAuthor[ExternalUsers.user]]?.value
                    if (linkedUid != null) {
                        Triple(
                            row[commentExternalLinkedUserDiary[Diaries.login]],
                            row[commentExternalLinkedUser[Users.nickname]],
                            viewerUserId == linkedUid
                        )
                    } else {
                        Triple(null, row[commentExternalAuthor[ExternalUsers.nickname]], false)
                    }
                }
                CommentAuthorType.ANONYMOUS -> {
                    Triple(null, row[commentAnonymousAuthor[AnonymousUsers.nickname]] ?: "anonymous", false)
                }
            }

            val canReact = isSelf || (canReactByPair[commentReactionGroupId to diaryOwnerId] ?: false)
            val inReply = row[Comments.parentComment]?.value?.let { parentId ->
                replyMeta[parentId]?.let { CommentDto.ReplyView(parentId, it.login, it.nickname) }
            }

            val view = CommentDto.View(
                id = row[Comments.id].value,
                authorLogin = authorLogin,
                authorNickname = authorNickname,
                postUri = postUri,
                diaryLogin = diaryLogin,
                avatar = row[Comments.avatar],
                text = row[Comments.text],
                creationTime = row[Comments.creationTime],
                isReactable = canReact,
                reactions = reactionsByComment[row[Comments.id].value] ?: emptyList(),
                reactionGroupId = commentReactionGroupId,
                inReplyTo = inReply
            )
            byPost.getOrPut(postId) { mutableListOf() }.add(view)
        }

        return byPost
    }

    private fun loadReplyMeta(parentIds: Set<UUID>): Map<UUID, ReplyMeta> {
        if (parentIds.isEmpty()) return emptyMap()

        val rows = Comments
            .leftJoin(Users, { Comments.localAuthor }, { Users.id })
            .leftJoin(Diaries, { Users.id }, { Diaries.owner })
            .leftJoin(ExternalUsers, { Comments.externalAuthor }, { ExternalUsers.id })
            .leftJoin(replyExtLinkUser, { ExternalUsers.user }, { replyExtLinkUser[Users.id] })
            .leftJoin(replyExtLinkDiary, { replyExtLinkUser[Users.id] }, { replyExtLinkDiary[Diaries.owner] })
            .leftJoin(AnonymousUsers, { Comments.anonymousAuthor }, { AnonymousUsers.id })
            .slice(
                Comments.id, Comments.authorType,
                Users.nickname, Diaries.login,
                ExternalUsers.nickname,
                replyExtLinkUser[Users.nickname],
                replyExtLinkDiary[Diaries.login],
                AnonymousUsers.nickname
            )
            .select { Comments.id inList parentIds.toList() }
            .toList()

        return rows.associate { row ->
            val (login, nickname) = when (row[Comments.authorType]) {
                CommentAuthorType.LOCAL ->
                    row[Diaries.login] to row[Users.nickname]
                CommentAuthorType.EXTERNAL -> {
                    val linkedLogin = row[replyExtLinkDiary[Diaries.login]]
                    val linkedNick = row[replyExtLinkUser[Users.nickname]] ?: row[ExternalUsers.nickname]
                    linkedLogin to linkedNick
                }
                CommentAuthorType.ANONYMOUS ->
                    null to (row[AnonymousUsers.nickname] ?: "anonymous")
            }
            row[Comments.id].value to ReplyMeta(login, nickname)
        }
    }
}
