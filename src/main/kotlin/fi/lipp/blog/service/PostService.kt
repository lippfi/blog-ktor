package fi.lipp.blog.service

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import kotlinx.datetime.LocalDateTime
import java.util.UUID

interface PostService {
    fun getPostForEdit(userId: UUID, postId: UUID): PostDto.Update

    fun getPreface(userId: UUID?, diaryId: UUID): PostDto.View?

    fun getPost(userId: UUID?, diaryLogin: String, uri: String): PostDto.View

    fun getPosts(
        userId: UUID?,
        authorId: UUID?,
        diaryId: UUID?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<PostDto.View>

    fun addPost(userId: UUID, post: PostDto.Create)
    fun updatePost(userId: UUID, post: PostDto.Update)
    fun deletePost(userId: UUID, postId: UUID)

    fun addComment(userId: UUID, comment: CommentDto.Create)
    fun updateComment(userId: UUID, comment: CommentDto.Update)
    fun deleteComment(userId: UUID, commentId: UUID)
}
