package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

data class Comment(
    val id: UUID?,
    val postId: UUID,

    val avatar : String,
    val authorNickname : String,
    val authorLogin: String,

    val text: String,
    val creationTime : LocalDateTime,
)