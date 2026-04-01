package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.PostDto
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.select
import java.util.UUID

internal class PostViewLoader(
    private val accessGroupService: AccessGroupService,
    private val postQueryHelper: PostQueryHelper,
    private val reactionLoader: ReactionLoader,
    private val commentViewLoader: CommentViewLoader,
) {

    data class AccessChecks(
        val canComment: Boolean,
        val canReact: Boolean,
    )

    data class PostViewDependencies(
        val accessChecks: Map<UUID, AccessChecks>,
        val tagsByPost: Map<UUID, Set<String>>,
        val commentCountsByPost: Map<UUID, Int>,
        val reactionsByPost: Map<UUID, List<ReactionDto.ReactionInfo>>,
    )

    fun canReadPost(viewer: Viewer, row: ResultRow, diaryOwnerId: UUID): Boolean {
        return accessGroupService.inGroup(viewer, row[Posts.readGroup].value, diaryOwnerId) ||
                ((viewer as? Viewer.Registered)?.userId?.let { uid ->
                    row.postAuthorUserId() == uid
                } ?: false)
    }

    fun loadPostViewDependencies(
        transaction: Transaction,
        viewer: Viewer,
        rowsByPostId: Map<UUID, ResultRow>
    ): PostViewDependencies {
        val postIds = rowsByPostId.keys
        val commentVisibilityData = commentViewLoader.loadVisibilityData(viewer, postIds)
        return PostViewDependencies(
            accessChecks = getBulkAccessGroupChecks(viewer, rowsByPostId),
            tagsByPost = loadTagsForPosts(postIds),
            commentCountsByPost = commentViewLoader.loadVisibleCommentCountsForPosts(
                transaction, viewer, postIds, commentVisibilityData
            ),
            reactionsByPost =  reactionLoader.loadPostReactions(transaction, viewer, postIds)
        )
    }

    fun toPostView(
        transaction: Transaction,
        viewer: Viewer,
        postEntity: PostEntity
    ): PostDto.View {
        return toPostViewById(transaction, viewer, postEntity.id.value)
    }

    fun toPostView(
        transaction: Transaction,
        viewer: Viewer,
        row: ResultRow
    ): PostDto.View {
        val postId = row[Posts.id].value
        val dependencies = loadPostViewDependencies(transaction, viewer, mapOf(postId to row))

        return toPostView(
            row = row,
            commentsCount = dependencies.commentCountsByPost[postId] ?: 0,
            reactions = dependencies.reactionsByPost[postId] ?: emptyList(),
            tags = dependencies.tagsByPost[postId] ?: emptySet(),
            accessGroupChecks = dependencies.accessChecks.getValue(postId)
        )
    }

    fun toPostViewById(
        transaction: Transaction,
        viewer: Viewer,
        postId: UUID
    ): PostDto.View {
        val row = postQueryHelper.loadVisibleSinglePostRowOrThrow(viewer) {
            Posts.id eq postId
        }

        return toPostView(transaction, viewer, row)
    }

    fun toPostView(
        row: ResultRow,
        commentsCount: Int,
        reactions: List<ReactionDto.ReactionInfo>,
        tags: Set<String>,
        accessGroupChecks: AccessChecks,
    ): PostDto.View {
        val authorLogin: String?
        val authorNickname: String
        val authorSignature: String?

        if (row[Posts.authorType] == PostAuthorType.LOCAL) {
            authorNickname = row[postQueryHelper.localPostAuthor[Users.nickname]]
            authorSignature = row[postQueryHelper.localPostAuthor[Users.signature]]
            authorLogin = row[postQueryHelper.localAuthorDiary[Diaries.login]]
        } else {
            val linkedUserId = row[postQueryHelper.externalPostAuthor[ExternalUsers.user]]
            if (linkedUserId != null) {
                authorNickname = row[postQueryHelper.externalUserLinkedUser[Users.nickname]]
                authorSignature = row[postQueryHelper.externalUserLinkedUser[Users.signature]]
                authorLogin = row[postQueryHelper.externalLinkedAuthorDiary[Diaries.login]]
            } else {
                authorNickname = row[postQueryHelper.externalPostAuthor[ExternalUsers.nickname]]
                authorSignature = null
                authorLogin = null
            }
        }

        return PostDto.View(
            id = row[Posts.id].value,
            uri = row[Posts.uri],
            avatar = row[Posts.avatar],
            authorNickname = authorNickname,
            authorLogin = authorLogin,
            authorSignature = authorSignature,
            diaryLogin = row[postQueryHelper.postDiary[Diaries.login]],
            title = row[Posts.title],
            text = row[Posts.text],
            creationTime = row[Posts.creationTime],
            isPreface = row[Posts.isPreface],
            isEncrypted = row[Posts.isEncrypted],
            isHidden = row[Posts.isHidden],
            tags = tags,
            classes = row[Posts.classes],
            isCommentable = accessGroupChecks.canComment,
            commentsCount = commentsCount,
            readGroupId = row[Posts.readGroup].value,
            commentGroupId = row[Posts.commentGroup].value,
            reactionGroupId = row[Posts.reactionGroup].value,
            commentReactionGroupId = row[Posts.commentReactionGroup].value,
            isReactable = accessGroupChecks.canReact,
            reactions = reactions,
        )
    }

    private fun ResultRow.postAuthorUserId(): UUID? {
        return when (this[Posts.authorType]) {
            PostAuthorType.LOCAL -> this[postQueryHelper.localPostAuthor[Users.id]].value
            PostAuthorType.EXTERNAL -> this[postQueryHelper.externalPostAuthor[ExternalUsers.user]]?.value
        }
    }

    private fun loadTagsForPosts(postIds: Set<UUID>): Map<UUID, Set<String>> {
        if (postIds.isEmpty()) return emptyMap()

        return PostTags
            .innerJoin(Tags)
            .slice(PostTags.post, Tags.name)
            .select { PostTags.post inList postIds.toList() }
            .groupBy { it[PostTags.post].value }
            .mapValues { (_, rows) ->
                rows.map { it[Tags.name] }.toSet()
            }
    }


    private fun getBulkAccessGroupChecks(
        viewer: Viewer,
        results: Map<UUID, ResultRow>
    ): Map<UUID, AccessChecks> {
        val userId = (viewer as? Viewer.Registered)?.userId
        val groupIdToOwnerId = mutableSetOf<Pair<UUID, UUID>>()
        val postIdToAuthorUserId = mutableMapOf<UUID, UUID?>()

        results.forEach { (postId, row) ->
            val diaryOwnerId = row[postQueryHelper.postDiary[Diaries.owner]].value
            val authorUserId = row.postAuthorUserId()
            postIdToAuthorUserId[postId] = authorUserId

            if (userId != authorUserId) {
                groupIdToOwnerId.add(row[Posts.commentGroup].value to diaryOwnerId)
                groupIdToOwnerId.add(row[Posts.reactionGroup].value to diaryOwnerId)
            }
        }

        val groupResults = accessGroupService.bulkCheckGroups(viewer, groupIdToOwnerId)

        return results.mapValues { (postId, row) ->
            val diaryOwnerId = row[postQueryHelper.postDiary[Diaries.owner]].value
            val authorUserId = postIdToAuthorUserId[postId]
            val self = userId == authorUserId

            val canComment =
                self || (groupResults[row[Posts.commentGroup].value to diaryOwnerId] ?: false)
            val canReact =
                self || (groupResults[row[Posts.reactionGroup].value to diaryOwnerId] ?: false)

            AccessChecks(
                canComment = canComment,
                canReact = canReact,
            )
        }
    }
}