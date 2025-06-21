package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.CommentWebSocketMessage
import fi.lipp.blog.data.webSocketJson
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Posts
import fi.lipp.blog.service.CommentWebSocketService
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommentWebSocketServiceImpl : CommentWebSocketService {
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    private val sessionToPost = ConcurrentHashMap<WebSocketSession, UUID>()

    override suspend fun addSession(postId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(postId) { mutableSetOf() }.add(session)
        sessionToPost[session] = postId
    }

    override suspend fun removeSession(session: WebSocketSession) {
        val postId = sessionToPost[session] ?: return
        sessions[postId]?.remove(session)

        if (sessions[postId]?.isEmpty() == true) {
            sessions.remove(postId)
        }
        sessionToPost.remove(session)
    }

    override fun notifyCommentAdded(comment: CommentDto.View) {
        val postId = getPostIdFromComment(comment)
        val message = CommentWebSocketMessage.CommentAdded(comment)
        GlobalScope.launch {
            sendToPost(postId, message)
        }
    }

    override fun notifyCommentUpdated(comment: CommentDto.View) {
        val postId = getPostIdFromComment(comment)
        val message = CommentWebSocketMessage.CommentUpdated(comment)
        GlobalScope.launch {
            sendToPost(postId, message)
        }
    }

    override fun notifyCommentDeleted(commentId: UUID, postId: UUID) {
        val message = CommentWebSocketMessage.CommentDeleted(commentId)
        GlobalScope.launch {
            sendToPost(postId, message)
        }
    }

    private suspend fun sendToPost(postId: UUID, message: CommentWebSocketMessage) {
        val postSessions = sessions[postId] ?: return
        val jsonMessage = webSocketJson.encodeToString(message)

        postSessions.forEach { session ->
            try {
                session.send(Frame.Text(jsonMessage))
            } catch (e: Exception) {
                removeSession(session)
            }
        }
    }

    private fun getPostIdFromComment(comment: CommentDto.View): UUID {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq comment.diaryLogin }.singleOrNull()
                ?: throw Exception("Diary not found: ${comment.diaryLogin}")

            val postEntity = PostEntity.find {
                (Posts.diary eq diaryEntity.id) and (Posts.uri eq comment.postUri) 
            }.singleOrNull() ?: throw Exception("Post not found: ${comment.postUri}")

            postEntity.id.value
        }
    }
}
