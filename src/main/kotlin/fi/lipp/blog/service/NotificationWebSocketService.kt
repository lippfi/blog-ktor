package fi.lipp.blog.service

import fi.lipp.blog.data.NotificationDto
import io.ktor.websocket.*
import java.util.UUID

interface NotificationWebSocketService {
    /**
     * Register a WebSocket session for a user to receive real-time notifications.
     */
    suspend fun addSession(userId: UUID, session: WebSocketSession)

    /**
     * Remove a WebSocket session.
     */
    suspend fun removeSession(session: WebSocketSession)

    /**
     * Push a notification to the user via WebSocket if they have an active session.
     */
    fun sendNotification(userId: UUID, notification: NotificationDto)
}
