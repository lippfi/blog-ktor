package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

data class PostView(
    val id: UUID,
    val uri : String,

    val avatar : String,
    val authorNickname : String,
    val authorLogin: String,

    val title : String,
    val text : String,
    val creationTime : LocalDateTime,

    val isPreface : Boolean,
    val isEncrypted: Boolean,

    val classes : String,
    var tags : Set<String>,

    val isCommentable: Boolean,
    val comments: List<CommentView>,
)

class PostPostData(
    val uri : String,
    val avatar : String,

    val title : String,
    val text : String,

    val readGroupId: UUID,
    val commentGroupId: UUID,

    var tags : Set<String>,
    val classes : String,

    val isPreface : Boolean,
    val isEncrypted: Boolean,
)

class PostUpdateData(
    val id: UUID,
    val uri : String,
    val avatar : String,

    val title : String,
    val text : String,

    val readGroupId: UUID,
    val commentGroupId: UUID,

    var tags : Set<String>,
    val classes : String,

    val isEncrypted: Boolean,
)
