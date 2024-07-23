package fi.lipp.blog.service

import fi.lipp.blog.data.Comment
import fi.lipp.blog.model.Page
import fi.lipp.blog.data.Post
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import kotlinx.datetime.LocalDateTime
import java.util.UUID

interface PostService {
    fun getPreface(userId: Long, diaryId: Long): Post?

    fun getPost(userId: Long, authorLogin: String, uri: String): Post?

    fun getPosts(
        userId: Long,
        authorId: Long?,
        diaryId: Long?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<Post>

    fun addPost(userId: Long, post: Post)
    fun updatePost(userId: Long, post: Post)
    fun deletePost(userId: Long, postId: UUID)

    fun addComment(userId: Long, comment: Comment)
    fun updateComment(userId: Long, comment: Comment)
    fun deleteComment(userId: Long, commentId: UUID)
}
