package fi.lipp.blog.mapper

import fi.lipp.blog.data.CommentView
import fi.lipp.blog.data.PostUpdateData
import fi.lipp.blog.data.PostView
import fi.lipp.blog.domain.CommentEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import java.util.*

class PostMapperImpl(private val accessGroupService: AccessGroupService) : PostMapper {
    override fun toPostView(userId: Long?, postEntity: PostEntity): PostView {
        val author = UserEntity.findById(postEntity.authorId) ?: throw InternalServerError()
        val isCommentable = (userId == postEntity.authorId.value) || (accessGroupService.inGroup(userId, postEntity.commentGroupId.value))

        return PostView(
            id = postEntity.id.value,
            uri = postEntity.uri,
            authorLogin = author.login,
            authorNickname = author.nickname,
            avatar = postEntity.avatar,
            title = postEntity.title,
            text = postEntity.text,
            creationTime = postEntity.creationTime,
            isEncrypted = postEntity.isEncrypted,
            isPreface = postEntity.isPreface,
            classes = postEntity.classes,
            tags = postEntity.tags.map { it.name }.toSet(),
            isCommentable = isCommentable,
            comments = getCommentsForPost(postEntity.id.value)
        )
    }

    override fun toPostView(userId: Long?, row: ResultRow): PostView {
        return PostView(
            id = row[Posts.id].value,
            uri = row[Posts.uri],

            avatar = row[Posts.avatar],
            authorNickname = row[Users.nickname],
            authorLogin = row[Users.login],

            title = row[Posts.title],
            text = row[Posts.text],
            creationTime = row[Posts.creationTime],

            isPreface = row[Posts.isPreface],
            isEncrypted = row[Posts.isEncrypted],

            tags = getTagsForPost(row[Posts.id].value),

            classes = row[Posts.classes],
            isCommentable = (userId == row[Users.id].value) || (accessGroupService.inGroup(userId, row[Posts.commentGroup].value)),
            comments = getCommentsForPost(row[Posts.id].value)
        )
    }

    override fun toPostUpdateData(postEntity: PostEntity): PostUpdateData {
        return PostUpdateData(
            id = postEntity.id.value,
            uri = postEntity.uri,
            avatar = postEntity.avatar,

            title = postEntity.title,
            text = postEntity.text,
            
            readGroupId = postEntity.readGroupId.value,
            commentGroupId = postEntity.commentGroupId.value,

            tags = postEntity.tags.map { it.name }.toSet(),
            classes = postEntity.classes,

            isEncrypted = postEntity.isEncrypted,
        )
    }

    private fun getTagsForPost(postId: UUID): Set<String> {
        return PostTags
            .innerJoin(Tags)
            .slice(Tags.name)
            .select { PostTags.post eq postId }
            .map { it[Tags.name] }
            .toSet()
    }

    private fun getCommentsForPost(postId: UUID): List<CommentView> {
        return CommentEntity.find { Comments.post eq postId }.orderBy(Comments.creationTime to SortOrder.ASC).map { it.toComment() }
    }
}