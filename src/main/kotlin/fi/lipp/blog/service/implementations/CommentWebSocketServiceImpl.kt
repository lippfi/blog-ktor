package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.CommentWebSocketMessage
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.webSocketJson
import fi.lipp.blog.domain.CommentEntity
import fi.lipp.blog.service.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommentWebSocketServiceImpl(
    private val accessGroupService: AccessGroupService
) : CommentWebSocketService, KoinComponent {
    private val reactionService: ReactionService by inject()

    private val sessions = ConcurrentHashMap<UUID, MutableSet<SessionInfo>>()

    private val sessionToPost = ConcurrentHashMap<WebSocketSession, UUID>()

    data class SessionInfo(val session: WebSocketSession, val viewer: Viewer)

    override suspend fun addSession(postId: UUID, viewer: Viewer, session: WebSocketSession) {
        sessions.computeIfAbsent(postId) { mutableSetOf() }.add(SessionInfo(session, viewer))
        sessionToPost[session] = postId
    }

    override suspend fun removeSession(session: WebSocketSession) {
        val postId = sessionToPost[session] ?: return
        sessions[postId]?.removeIf { it.session == session }

        if (sessions[postId]?.isEmpty() == true) {
            sessions.remove(postId)
        }
        sessionToPost.remove(session)
    }

    override fun notifyCommentAdded(comment: CommentEntity) {
        GlobalScope.launch {
            notifySubscribers(comment) { CommentWebSocketMessage.CommentAdded(it) }
        }
    }

    override fun notifyCommentUpdated(comment: CommentEntity) {
        GlobalScope.launch {
            notifySubscribers(comment) { CommentWebSocketMessage.CommentUpdated(it) }
        }
    }

    override fun notifyCommentDeleted(commentId: UUID, postId: UUID) {
        val message = CommentWebSocketMessage.CommentDeleted(commentId)
        GlobalScope.launch {
            sendSimpleMessage(postId, message)
        }
    }

    override fun notifyReactionAdded(commentId: UUID, reaction: ReactionDto.ReactionInfo, postId: UUID) {
        val message = CommentWebSocketMessage.ReactionAdded(commentId, reaction)
        GlobalScope.launch {
            sendSimpleMessage(postId, message)
        }
    }

    override fun notifyReactionRemoved(commentId: UUID, reaction: ReactionDto.ReactionInfo, postId: UUID) {
        val message = CommentWebSocketMessage.ReactionRemoved(commentId, reaction)
        GlobalScope.launch {
            sendSimpleMessage(postId, message)
        }
    }

    private suspend fun notifySubscribers(commentEntity: CommentEntity, messageFactory: (CommentDto.View) -> CommentWebSocketMessage) {
        val postId = transaction { commentEntity.postId.value }
        val postSessions = sessions[postId] ?: return

        postSessions.forEach { sessionInfo ->
            val message = transaction {
                val view = commentEntity.toComment(this, sessionInfo.viewer, accessGroupService, reactionService)
                messageFactory(view)
            }

            val jsonMessage = webSocketJson.encodeToString(message)
            try {
                sessionInfo.session.send(Frame.Text(jsonMessage))
            } catch (e: Exception) {
                removeSession(sessionInfo.session)
            }
        }
    }

    private suspend fun sendSimpleMessage(postId: UUID, message: CommentWebSocketMessage) {
        val postSessions = sessions[postId] ?: return
        val jsonMessage = webSocketJson.encodeToString(message)

        postSessions.forEach { sessionInfo ->
            try {
                sessionInfo.session.send(Frame.Text(jsonMessage))
            } catch (e: Exception) {
                removeSession(sessionInfo.session)
            }
        }
    }
}
