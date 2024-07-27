package fi.lipp.blog.domain

import fi.lipp.blog.repository.PostTags
import fi.lipp.blog.repository.Posts
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

    var isArchived by Posts.isArchived

    var classes by Posts.classes
    var tags by TagEntity via PostTags

    var readGroupId by Posts.readGroup
    var commentGroupId by Posts.commentGroup
}