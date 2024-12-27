package fi.lipp.blog.service

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import kotlinx.datetime.LocalDateTime
import java.util.UUID

interface PostService {
    fun getPostForEdit(userId: Long, postId: UUID): PostDto.Update

    fun getPreface(userId: Long?, diaryId: Long): PostDto.View?

    fun getPost(userId: Long?, authorLogin: String, uri: String): PostDto.View

    fun getPosts(
        userId: Long?,
        authorId: Long?,
        diaryId: Long?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<PostDto.View>

    fun addPost(userId: Long, post: PostDto.Create)
    fun updatePost(userId: Long, post: PostDto.Update)
    fun deletePost(userId: Long, postId: UUID)

    fun addComment(userId: Long, comment: CommentDto.Create)
    fun updateComment(userId: Long, comment: CommentDto.Update)
    fun deleteComment(userId: Long, commentId: UUID)
}
