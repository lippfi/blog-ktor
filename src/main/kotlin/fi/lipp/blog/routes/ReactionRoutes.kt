package fi.lipp.blog.routes

import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.reactionRoutes(reactionService: ReactionService) {
    route("/reactions") {
        get {
            val reactions = reactionService.getReactions()
            call.respond(reactions)
        }

        get("/search") {
            val pattern = call.request.queryParameters["pattern"] ?: ""
            val reactions = reactionService.searchReactionsByName(pattern)
            call.respond(reactions)
        }

        authenticate {
            post("/create") {
                val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                if (files.isEmpty()) {
                    throw IllegalArgumentException("No icon file provided")
                }
                val reaction = reactionService.createReaction(userId, name, files.first())
                call.respond(reaction)
            }

            delete("/{name}") {
                val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                reactionService.deleteReaction(userId, name)
                call.respondText("Reaction deleted successfully")
            }

            get("/recent") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val reactions = reactionService.getUserRecentReactions(userId, limit)
                call.respond(reactions)
            }
        }

        // Post reactions
        post("/{diaryLogin}/{uri}/{reactionId}") {
            val diaryLogin = call.parameters["diaryLogin"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
            val uri = call.parameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
            val reactionId = call.parameters["reactionId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing reactionId parameter")

            val viewer = call.principal<JWTPrincipal>()?.let { Viewer.Registered(userId) }
                ?: Viewer.Anonymous(call.request.local.remoteHost, call.request.headers["User-Agent"] ?: "unknown")

            reactionService.addReaction(viewer, diaryLogin, uri, reactionId)
            call.respondText("Reaction added successfully")
        }

        delete("/{diaryLogin}/{uri}/{reactionId}") {
            val diaryLogin = call.parameters["diaryLogin"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
            val uri = call.parameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
            val reactionId = call.parameters["reactionId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing reactionId parameter")

            val viewer = call.principal<JWTPrincipal>()?.let { Viewer.Registered(userId) }
                ?: Viewer.Anonymous(call.request.local.remoteHost, call.request.headers["User-Agent"] ?: "unknown")

            reactionService.removeReaction(viewer, diaryLogin, uri, reactionId)
            call.respondText("Reaction removed successfully")
        }

        // Comment reactions
        post("/{diaryLogin}/{uri}/comments/{commentId}/{reactionId}") {
            val diaryLogin = call.parameters["diaryLogin"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
            val uri = call.parameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
            val commentId = call.parameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
            val reactionId = call.parameters["reactionId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing reactionId parameter")

            val viewer = call.principal<JWTPrincipal>()?.let { Viewer.Registered(userId) }
                ?: Viewer.Anonymous(call.request.origin.remoteHost, call.request.headers["User-Agent"] ?: "unknown")

            reactionService.addCommentReaction(viewer, diaryLogin, uri, commentId, reactionId)
            call.respondText("Comment reaction added successfully")
        }

        delete("/{diaryLogin}/{uri}/comments/{commentId}/{reactionId}") {
            val diaryLogin = call.parameters["diaryLogin"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
            val uri = call.parameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
            val commentId = call.parameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
            val reactionId = call.parameters["reactionId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing reactionId parameter")

            val viewer = call.principal<JWTPrincipal>()?.let { Viewer.Registered(userId) }
                ?: Viewer.Anonymous(call.request.origin.remoteHost, call.request.headers["User-Agent"] ?: "unknown")

            reactionService.removeCommentReaction(viewer, diaryLogin, uri, commentId, reactionId)
            call.respondText("Comment reaction removed successfully")
        }

        get("/comments/{commentId}") {
            val commentId = call.parameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
            val reactions = reactionService.getCommentReactions(commentId)
            call.respond(reactions)
        }
    }
}
