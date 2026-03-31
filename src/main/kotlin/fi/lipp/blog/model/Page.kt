package fi.lipp.blog.model

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.util.SerializableMap
import kotlinx.serialization.Serializable

@Serializable
data class Page<T>(
    var content: List<T>,
    var currentPage: Int,
    var totalPages: Int
)

@Serializable
data class PostPage(
    val diary: DiaryView,
    val post: PostDto.View,
    val comments: List<CommentDto.View>,
)

@Serializable
data class DiaryPage(
    val diary: DiaryView,
    val posts: Page<PostDto.View>,
)

@Serializable
data class DiaryView(
    val name: String,
    val subtitle: String,
    val styles: List<String>,
    @Serializable
    val defaultGroups: SerializableMap?,
)

@Serializable
data class UserProfilePage(
    val login: String,
    val nickname: String,

    val content: String,

    val song: String?,
    val styles: List<String>,

    val friends: List<UserDto.View>,
)