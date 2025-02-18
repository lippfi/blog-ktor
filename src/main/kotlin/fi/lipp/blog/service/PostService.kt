package fi.lipp.blog.service

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import kotlinx.datetime.LocalDateTime
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

interface PostService {
    fun getPostForEdit(userId: UUID, postId: UUID): PostDto.Update

    fun getPreface(viewer: Viewer, diaryLogin: String): PostDto.View?

    fun getPost(viewer: Viewer, diaryLogin: String, uri: String): PostDto.View

    fun getPosts(
        viewer: Viewer,
        authorLogin: String?,
        diaryLogin: String?,
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

    /**
     * Get posts from users that the specified user is following
     * @param userId ID of the user whose followed posts to retrieve
     * @param pageable pagination parameters
     * @return Page of posts from followed users
     */
    fun getFollowedPosts(userId: UUID, pageable: Pageable): Page<PostDto.View>

    /**
     * Get latest posts for a specific viewer
     * @param viewer the viewer requesting the posts (registered or anonymous)
     * @param pageable pagination parameters
     * @return Page of posts ordered by creation date
     */
    fun getPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View>

    /**
     * Get posts sorted by the time of their latest comment
     * @param viewer the viewer requesting the posts (registered or anonymous)
     * @param pageable pagination parameters
     * @return Page of posts ordered by last comment time
     */
    fun getDiscussedPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View>
}

sealed interface Viewer {
    class Registered(val userId: UUID) : Viewer
    class Anonymous(ip: String, fingerprint: String) : Viewer {
        companion object {
            val password: PasswordEncoder by inject(PasswordEncoder::class.java)
        }
        val ipFingerprint = password.encode(ip + fingerprint) 
    }
}
