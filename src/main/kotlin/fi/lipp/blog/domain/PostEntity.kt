package fi.lipp.blog.domain

import fi.lipp.blog.data.Post
import fi.lipp.blog.domain.CommentEntity.Companion.referrersOn
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.PostTags
import fi.lipp.blog.repository.Posts
import fi.lipp.blog.repository.Users
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
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
    var isPrivate by Posts.isPrivate

    var isArchived by Posts.isArchived

    var classes by Posts.classes
    var tags by TagEntity via PostTags

    fun toPost(): Post {
        val author = UserEntity.findById(authorId) ?: throw InternalServerError()
        return Post(
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
            isPrivate = isPrivate,
            classes = classes,
            tags = tags.map { it.name }.toSet(),
        )
    }
}