package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Comments : UUIDTable() {
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val authorType = enumerationByName("author_type", 32, CommentAuthorType::class)
    val localAuthor = reference("local_author", Users, onDelete = ReferenceOption.CASCADE).nullable().index("idx_comment_local_author")
    val externalAuthor = reference("external_author", ExternalUsers, onDelete = ReferenceOption.CASCADE).nullable().index("idx_comment_external_author")
    val anonymousAuthor = reference("anonymous_author", AnonymousUsers, onDelete = ReferenceOption.CASCADE).nullable().index("idx_comment_anonymous_author")

    val avatar = varchar("avatar", 1024)
    val text = text("text")
    val creationTime = datetime("creation_time").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    val parentComment = reference("parent_comment", Comments).nullable()
    val reactionGroup = reference("reaction_group", AccessGroups, onDelete = ReferenceOption.CASCADE)
    val isPublished = bool("is_published").clientDefault { true }
}

enum class CommentAuthorType {
    LOCAL,
    EXTERNAL,
    ANONYMOUS,
}

object CommentReactions : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val comment = reference("comment", Comments, onDelete = ReferenceOption.CASCADE)
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("comment_reactions_unique", user, comment, reaction)
    }
}

object AnonymousCommentReactions : UUIDTable() {
    val ipFingerprint = varchar("ip_fingerprint", 2048)
    val comment = reference("comment", Comments, onDelete = ReferenceOption.CASCADE)
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("anonymous_comment_reactions_unique", ipFingerprint, comment, reaction)
    }
}
