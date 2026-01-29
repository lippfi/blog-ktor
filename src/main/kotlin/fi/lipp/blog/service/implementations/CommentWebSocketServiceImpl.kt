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
        println("added session for viewer $viewer")
    }

    override suspend fun removeSession(session: WebSocketSession) {
        val postId = sessionToPost[session] ?: return
        sessions[postId]?.removeIf { it.session == session }

        if (sessions[postId]?.isEmpty() == true) {
            sessions.remove(postId)
        }
        sessionToPost.remove(session)
        println("removed session")
    }

    override fun notifyCommentAdded(comment: CommentEntity) {
        println("notifyCommentAdded start")
        val postId = comment.postId.value
        val commentId = comment.id.value
        GlobalScope.launch {
            notifySubscribers(postId, commentId) { CommentWebSocketMessage.CommentAdded(it) }
        }
        println("notifyCommentAdded end")
    }

    override fun notifyCommentUpdated(comment: CommentEntity) {
        val postId = comment.postId.value
        val commentId = comment.id.value
        GlobalScope.launch {
            notifySubscribers(postId, commentId) { CommentWebSocketMessage.CommentUpdated(it) }
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

    private suspend fun notifySubscribers(postId: UUID, commentId: UUID, messageFactory: (CommentDto.View) -> CommentWebSocketMessage) {
        println("in notifySubscribers")
        val postSessions = sessions[postId] ?: return

        println("sessions: ${postSessions.count()}")
        postSessions.forEach { sessionInfo ->
            val message = transaction {
                println("searching entity for $commentId")
                val entity = CommentEntity.findById(commentId) ?: return@transaction null
                println("entity - $entity")
                val view = entity.toComment(this, sessionInfo.viewer, accessGroupService, reactionService)
                println("view - $view")
                messageFactory(view)
            } ?: return@forEach

            val jsonMessage = webSocketJson.encodeToString(message)
            println("json $jsonMessage")
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
