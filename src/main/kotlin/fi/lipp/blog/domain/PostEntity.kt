package fi.lipp.blog.domain

import fi.lipp.blog.data.AccessGroupType
import fi.lipp.blog.data.PostFull
import fi.lipp.blog.data.PostView
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.Comments
import fi.lipp.blog.repository.CustomGroupUsers
import fi.lipp.blog.repository.PostTags
import fi.lipp.blog.repository.Posts
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.util.*

class PostEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostEntity>(Posts)

    var uri by Posts.uri

    val diaryId by Posts.diary
    val authorId by Posts.author

    // TODO better way to store file and one-time avatars
    var avatar by Posts.avatar
    var title by Posts.title
    var text by Posts.text
    val creationTime by Posts.creationTime

    var isEncrypted by Posts.isEncrypted
    val isPreface by Posts.isPreface

    var isArchived by Posts.isArchived

    var classes by Posts.classes
    var tags by TagEntity via PostTags

    var readGroupId by Posts.readGroup
    var commentGroupId by Posts.commentGroup

    // TODO avoid duplicating code (getting tags and comments)
    fun toPostView(userId: Long?): PostView {
        val author = UserEntity.findById(authorId) ?: throw InternalServerError()
        val commentGroup = AccessGroupEntity.findById(commentGroupId) ?: throw InternalServerError()
        val isCommentable = when (commentGroup.type) {
            AccessGroupType.PRIVATE -> userId == authorId.value
            AccessGroupType.EVERYONE -> true
            AccessGroupType.REGISTERED_USERS -> userId != null
            AccessGroupType.CUSTOM -> {
                CustomGroupUsers
                    .select { (CustomGroupUsers.accessGroup eq commentGroupId) and (CustomGroupUsers.member eq userId) }
                    .count() > 0
            }
        }

        return PostView(
            id = id.value,
            uri = uri,
            authorLogin = author.login,
            authorNickname = author.nickname,
            avatar = avatar,
            title = title,
            text = text,
            creationTime = creationTime,
            isEncrypted = isEncrypted,
            isPreface = isPreface,
            classes = classes,
            tags = tags.map { it.name }.toSet(),
            isCommentable = isCommentable,
            comments = CommentEntity.find { Comments.post eq id }.orderBy(Comments.creationTime to SortOrder.ASC).map { it.toComment() },
        )
    }

    fun toPostFull(): PostFull {
        val author = UserEntity.findById(authorId) ?: throw InternalServerError()
        return PostFull(
            id = id.value,
            uri = uri,
            authorLogin = author.login,
            authorNickname = author.nickname,
            avatar = avatar,
            title = title,
            text = text,
            creationTime = creationTime,
            isEncrypted = isEncrypted,
            isPreface = isPreface,
            classes = classes,
            tags = tags.map { it.name }.toSet(),
            readGroupId = readGroupId.value,
            commentGroupId = commentGroupId.value,
        )
    }
}