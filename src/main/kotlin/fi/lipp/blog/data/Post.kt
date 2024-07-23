package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

data class Post(
    val id: UUID?,
    val uri : String,

    val avatar : String,
    val authorNickname : String,
    val authorLogin: String,

    val title : String,
    val text : String,
    val creationTime : LocalDateTime,

    val isPreface : Boolean,
    val isPrivate : Boolean,
    val isEncrypted: Boolean,

    val classes : String,
    var tags : Set<String>,
)