package fi.lipp.blog.routes

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

            get("/dialog-messages") {
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

            get("/messages") {
                val login = call.request.queryParameters["login"] ?: return@get call.respond(HttpStatusCode.BadRequest, "login is required")
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10
                val direction = when (call.request.queryParameters["direction"]?.uppercase()) {
                    "DESC" -> SortOrder.DESC
                    else -> SortOrder.ASC
                }
                val pageable = Pageable(page, size, direction)
                val messages = dialogService.getMessages(userId, login, pageable)
                call.respond(messages)
            }

            post("/message") {
                val receiverLogin = call.request.queryParameters["login"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "receiver login is required")
                val message = call.receive<MessageDto.Create>()
                val createdMessage = dialogService.sendMessage(userId, receiverLogin, message)
                call.respond(createdMessage)
            }
        }
    }
}
