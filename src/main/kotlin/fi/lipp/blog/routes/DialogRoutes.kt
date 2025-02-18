package fi.lipp.blog.routes

import fi.lipp.blog.data.DialogDto
import fi.lipp.blog.data.MessageDto
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.DialogService
import org.jetbrains.exposed.sql.SortOrder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.dialogRoutes(dialogService: DialogService) {
    route("/dialog") {
        authenticate {
            // Get all dialogs for current user
            get("/list") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10
                val direction = when (call.request.queryParameters["direction"]?.uppercase()) {
                    "ASC" -> SortOrder.ASC
                    else -> SortOrder.DESC
                }
                val pageable = Pageable(page, size, direction)
                val dialogs = dialogService.getDialogs(userId, pageable)
                call.respond(dialogs)
            }

            // Get messages in a dialog
            get("/messages") {
                val dialogId = call.request.queryParameters["dialogId"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "dialogId is required")
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10
                val direction = when (call.request.queryParameters["direction"]?.uppercase()) {
                    "DESC" -> SortOrder.DESC
                    else -> SortOrder.ASC
                }
                val pageable = Pageable(page, size, direction)
                val messages = dialogService.getMessages(userId, dialogId, pageable)
                call.respond(messages)
            }

            // Send a message to a user
            post("/messages") {
                val receiverLogin = call.request.queryParameters["receiverLogin"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "receiverLogin is required")
                val message = call.receive<MessageDto.Create>()
                val createdMessage = dialogService.sendMessage(userId, receiverLogin, message)
                call.respond(createdMessage)
            }
        }
    }
}
