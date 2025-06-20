package fi.lipp.blog.model

import fi.lipp.blog.data.PostDto
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
    val style: String?,
    @Serializable
    val defaultGroups: SerializableMap?,
)