package fi.lipp.blog.service

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.CommentWebSocketMessage
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.domain.CommentEntity
import fi.lipp.blog.service.Viewer
import io.ktor.websocket.*
import java.util.UUID

interface CommentWebSocketService {
    /**
     * Add a WebSocket session for a specific post
     * @param postId ID of the post to subscribe to
     * @param viewer Viewer of the session
     * @param session WebSocket session
     */
    suspend fun addSession(postId: UUID, viewer: Viewer, session: WebSocketSession)

    /**
     * Remove a WebSocket session
     */
    suspend fun removeSession(session: WebSocketSession)

    /**
     * Notify all subscribers about a new comment
     * This method is not suspend to allow it to be called from non-suspend functions
     * It will handle the suspension internally
     */
    fun notifyCommentAdded(comment: CommentEntity)

    /**
     * Notify all subscribers about an updated comment
     * This method is not suspend to allow it to be called from non-suspend functions
     * It will handle the suspension internally
     */
    fun notifyCommentUpdated(comment: CommentEntity)

    /**
     * Notify all subscribers about a deleted comment
     * This method is not suspend to allow it to be called from non-suspend functions
     * It will handle the suspension internally
     */
    fun notifyCommentDeleted(commentId: UUID, postId: UUID)

    /**
     * Notify all subscribers about a reaction added to a comment
     * This method is not suspend to allow it to be called from non-suspend functions
     * It will handle the suspension internally
     */
    fun notifyReactionAdded(commentId: UUID, reaction: ReactionDto.ReactionInfo, postId: UUID)

    /**
     * Notify all subscribers about a reaction removed from a comment
     * This method is not suspend to allow it to be called from non-suspend functions
     * It will handle the suspension internally
     */
    fun notifyReactionRemoved(commentId: UUID, reaction: ReactionDto.ReactionInfo, postId: UUID)
}
