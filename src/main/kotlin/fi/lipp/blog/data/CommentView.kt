package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

data class CommentView(
    val id: UUID,

    val avatar : String,
    val authorNickname : String,
    val authorLogin: String,

    val text: String,
    val creationTime : LocalDateTime,
)

data class CommentPostData(
    val postId: UUID,
    val avatar : String,
    val text: String,
)

data class CommentUpdateData(
    val id: UUID,
    val postId: UUID,
    val avatar : String,
    val text: String,
)
