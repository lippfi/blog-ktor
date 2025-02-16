package fi.lipp.blog.routes

import fi.lipp.blog.model.Pageable
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.NotificationService
import org.jetbrains.exposed.sql.SortOrder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.notificationRoutes(notificationService: NotificationService) {
    authenticate {
        route("/notifications") {
            get {
                val notifications = notificationService.getNotifications(userId)
                call.respond(notifications)
            }

            get {
                val id = call.request.queryParameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid notification ID")
                val notification = notificationService.getNotification(userId, id)
                call.respond(notification)
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
        }

        route("/posts/notifications") {
            post("/read-all") {
                val postId = call.request.queryParameters["postId"]?.let { UUID.fromString(it) }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid post ID")
                notificationService.readAllPostNotifications(userId, postId)
                call.respondText("All post notifications marked as read")
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
}
