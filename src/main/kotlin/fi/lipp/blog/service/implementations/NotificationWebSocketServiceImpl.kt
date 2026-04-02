package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.NotificationDto
import fi.lipp.blog.service.NotificationWebSocketService
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NotificationWebSocketServiceImpl : NotificationWebSocketService {
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val sessionToUser = ConcurrentHashMap<WebSocketSession, UUID>()

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    override suspend fun addSession(userId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        sessionToUser[session] = userId
    }

    override suspend fun removeSession(session: WebSocketSession) {
        val userId = sessionToUser.remove(session) ?: return
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) {
            sessions.remove(userId)
        }
    }

    override fun sendNotification(userId: UUID, notification: NotificationDto) {
        val userSessions = sessions[userId] ?: return
        val jsonMessage = json.encodeToString<NotificationDto>(notification)

        GlobalScope.launch {
            userSessions.forEach { session ->
                try {
                    session.send(Frame.Text(jsonMessage))
                } catch (e: Exception) {
                    removeSession(session)
                }
            }
        }
    }
}
