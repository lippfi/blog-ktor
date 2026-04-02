package fi.lipp.blog.routes

import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.NotificationWebSocketService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.*

fun Route.notificationRoutes(notificationService: NotificationService, notificationWebSocketService: NotificationWebSocketService) {
    authenticate {
        route("/notifications") {
            get {
                val id = call.request.queryParameters["id"]
                if (id != null) {
                    val notificationId = try { UUID.fromString(id) } catch (e: Exception) {
                        return@get call.respond(HttpStatusCode.BadRequest, "Invalid notification ID")
                    }
                    val notification = notificationService.getNotification(userId, notificationId)
                    call.respond(notification)
                } else {
                    val notifications = notificationService.getNotifications(userId)
                    call.respond(notifications)
                }
            }

            get("/settings") {
                val settings = notificationService.getNotificationSettings(userId)
                call.respond(settings)
            }

            post("/read") {
                val id = call.request.queryParameters["id"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid notification ID")
                notificationService.markAsRead(userId, id)
                call.respondText("Notification marked as read")
            }

            post("/read-all") {
                notificationService.markAllAsRead(userId)
                call.respondText("All notifications marked as read")
            }

            delete {
                val id = call.request.queryParameters["id"]?.let { UUID.fromString(it) }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid notification ID")
                notificationService.deleteNotification(userId, id)
                call.respondText("Notification deleted")
            }

            delete("/all") {
                notificationService.deleteAllNotifications(userId)
                call.respondText("All notifications deleted")
            }
        }

        route("/posts/mentions") {
            post {
                val postId = call.request.queryParameters["postId"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid post ID")
                val login = call.request.queryParameters["login"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Login is required")

                try {
                    notificationService.notifyAboutPostMention(userId, postId, login)
                    call.respondText("User notified about mention in post")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Failed to notify user")
                }
            }
        }

        route("/comments/mentions") {
            post {
                val commentId = call.request.queryParameters["commentId"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid comment ID")
                val login = call.request.queryParameters["login"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Login is required")

                try {
                    notificationService.notifyAboutCommentMention(userId, commentId, login)
                    call.respondText("User notified about mention in comment")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Failed to notify user")
                }
            }
        }
    }

    authenticate(optional = true) {
        webSocket("/notifications/ws") {
            val userIdValue = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                ?.payload?.getClaim("userId")?.asString()
                ?.let { UUID.fromString(it) }

            if (userIdValue == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }

            notificationWebSocketService.addSession(userIdValue, this)
            try {
                for (frame in incoming) {
                    // Keep connection alive; no client messages expected
                }
            } catch (e: Exception) {
                // Connection closed
            } finally {
                notificationWebSocketService.removeSession(this)
            }
        }
    }
}
