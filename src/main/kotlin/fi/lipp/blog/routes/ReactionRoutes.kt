package fi.lipp.blog.routes

import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.plugins.viewer
import fi.lipp.blog.service.ReactionService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.reactionRoutes(reactionService: ReactionService) {
    route("/reactions") {
        get("/search") {
            val text = call.request.queryParameters["text"] ?: ""
            val reactions = reactionService.search(text)
            call.respond(reactions)
        }

        get {
            val reactions = reactionService.getReactions()
            call.respond(reactions)
        }

        get("/basic") {
            val reactions = reactionService.getBasicReactions()
            call.respond(reactions)
        }

        get("/search") {
            val pattern = call.request.queryParameters["pattern"] ?: ""
            val reactions = reactionService.searchReactionsByName(pattern)
            call.respond(reactions)
        }

        get("/search-names") {
            val names = (call.request.queryParameters["names"] ?: "").split(",").map { it.trim() }
            val reactions = reactionService.getReactions(names)
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

            delete {
                val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                reactionService.deleteReaction(userId, name)
                call.respondText("Reaction deleted successfully")
            }

            get("/recent") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val reactions = reactionService.getUserRecentReactions(userId, limit)
                call.respond(reactions)
            }
        }

        authenticate(optional = true) {
            post("/post-reaction") {
                val diaryLogin = call.request.queryParameters["login"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
                val uri = call.request.queryParameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
                val reactionName = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing reactionName parameter")

                reactionService.addReaction(viewer, diaryLogin, uri, reactionName)
                call.respondText("Reaction added successfully")
            }

            delete("/post-reaction") {
                val diaryLogin = call.request.queryParameters["login"] ?: throw IllegalArgumentException("Missing diaryLogin parameter")
                val uri = call.request.queryParameters["uri"] ?: throw IllegalArgumentException("Missing uri parameter")
                val reactionName = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing reactionName parameter")

                reactionService.removeReaction(viewer, diaryLogin, uri, reactionName)
                call.respondText("Reaction removed successfully")
            }

            // Comment reactions
            post("comment-reaction") {
                val commentId = call.request.queryParameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
                val reactionName = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing reactionName parameter")

                reactionService.addCommentReaction(viewer, commentId, reactionName)
                call.respondText("Comment reaction added successfully")
            }

            delete("comment-reaction") {
                val commentId = call.request.queryParameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
                val reactionName = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing reactionName parameter")

                reactionService.removeCommentReaction(viewer, commentId, reactionName)
                call.respondText("Comment reaction removed successfully")
            }
        }
    }
}
