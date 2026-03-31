package fi.lipp.blog.domain

import fi.lipp.blog.repository.PostAuthorType
import fi.lipp.blog.repository.PostTags
import fi.lipp.blog.repository.Posts
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class PostEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostEntity>(Posts)

    var uri by Posts.uri

    var diary by DiaryEntity referencedOn Posts.diary
    var authorType by Posts.authorType
    var localAuthor by UserEntity optionalReferencedOn Posts.localAuthor
    var externalAuthor by ExternalUserEntity optionalReferencedOn Posts.externalAuthor

    val diaryId: EntityID<UUID>
        get() = diary.id

    fun getEffectiveAuthor(): UserEntity? {
        return when (authorType) {
            PostAuthorType.LOCAL -> localAuthor
            PostAuthorType.EXTERNAL -> externalAuthor?.user
        }
    }

    val authorId: UUID?
        get() = getEffectiveAuthor()?.id?.value

    // TODO better way to store file and one-time avatars
    var avatar by Posts.avatar
    var title by Posts.title
    var text by Posts.text
    val creationTime by Posts.creationTime
    var isHidden by Posts.isHidden

    var isEncrypted by Posts.isEncrypted
    var isPreface by Posts.isPreface

    var isArchived by Posts.isArchived

    var classes by Posts.classes
    var tags by TagEntity via PostTags

    var readGroupId by Posts.readGroup
    var commentGroupId by Posts.commentGroup
    var reactionGroupId by Posts.reactionGroup
    var commentReactionGroupId by Posts.commentReactionGroup
    var reactionSubsetId by Posts.reactionSubset
}

sealed interface PostAuthorRef {
    data class Local(val user: UserEntity) : PostAuthorRef
    data class External(val externalUser: ExternalUserEntity) : PostAuthorRef
}
