package fi.lipp.blog.routes

import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.plugins.viewer
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
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

        get("/name-busy") {
            val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
            val isBusy = reactionService.isReactionNameBusy(name)
            call.respond(mapOf("busy" to isBusy))
        }

        authenticate {
            get("/my-packs") {
                val packs = reactionService.getMyPacks(viewer as Viewer.Registered)
                call.respond(packs)
            }

            post("/create") {
                val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                val packName = call.request.queryParameters["pack"] ?: throw IllegalArgumentException("Missing pack parameter")
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                if (files.isEmpty()) {
                    throw IllegalArgumentException("No icon file provided")
                }
                val registeredViewer = viewer as Viewer.Registered
                val reaction = reactionService.createReaction(registeredViewer, name, packName, files.first())
                call.respond(reaction)
            }

            delete {
                val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                reactionService.deleteReaction(viewer as Viewer.Registered, name)
                call.respondText("Reaction deleted successfully")
            }

            put("/rename") {
                val oldName = call.request.queryParameters["oldName"] ?: throw IllegalArgumentException("Missing oldName parameter")
                val newName = call.request.queryParameters["newName"] ?: throw IllegalArgumentException("Missing newName parameter")
                reactionService.renameReaction(viewer as Viewer.Registered, oldName, newName)
                call.respondText("Reaction renamed successfully")
            }

            get("/recent") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val reactions = reactionService.getUserRecentReactions(userId, limit)
                call.respond(reactions)
            }

            // Reaction pack management
            put("/pack") {
                val packName = call.request.queryParameters["pack"] ?: throw IllegalArgumentException("Missing pack parameter")
                val newName = call.request.queryParameters["newName"]
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                val newIcon = files.firstOrNull()
                val pack = reactionService.updateReactionPack(viewer as Viewer.Registered, packName, newName, newIcon)
                call.respond(pack)
            }

            // Pack collection management
            get("/collection") {
                val collection = reactionService.getPackCollection(viewer as Viewer.Registered)
                call.respond(collection)
            }

            post("/collection") {
                val packName = call.request.queryParameters["pack"] ?: throw IllegalArgumentException("Missing pack parameter")
                reactionService.addPackToCollection(viewer as Viewer.Registered, packName)
                call.respondText("Pack added to collection")
            }

            delete("/collection") {
                val packName = call.request.queryParameters["pack"] ?: throw IllegalArgumentException("Missing pack parameter")
                reactionService.removePackFromCollection(viewer as Viewer.Registered, packName)
                call.respondText("Pack removed from collection")
            }

            put("/collection/reorder") {
                val packName = call.request.queryParameters["pack"] ?: throw IllegalArgumentException("Missing pack parameter")
                val ordinal = call.request.queryParameters["ordinal"]?.toIntOrNull() ?: throw IllegalArgumentException("Missing or invalid ordinal parameter")
                reactionService.reorderPackInCollection(viewer as Viewer.Registered, packName, ordinal)
                call.respondText("Pack reordered in collection")
            }

            // Reaction subset management
            post("/subset") {
                val diaryLogin = call.request.queryParameters["login"] ?: throw IllegalArgumentException("Missing login parameter")
                val name = call.request.queryParameters["name"] ?: throw IllegalArgumentException("Missing name parameter")
                val reactionNames = (call.request.queryParameters["reactions"] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val subsetId = reactionService.createReactionSubset(viewer as Viewer.Registered, diaryLogin, name, reactionNames)
                call.respond(mapOf("id" to subsetId.toString()))
            }

            put("/subset") {
                val subsetId = call.request.queryParameters["id"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing id parameter")
                val name = call.request.queryParameters["name"]
                val reactionNames = call.request.queryParameters["reactions"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                reactionService.updateReactionSubset(viewer as Viewer.Registered, subsetId, name, reactionNames)
                call.respondText("Reaction subset updated successfully")
            }

            delete("/subset") {
                val subsetId = call.request.queryParameters["id"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing id parameter")
                reactionService.deleteReactionSubset(viewer as Viewer.Registered, subsetId)
                call.respondText("Reaction subset deleted successfully")
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

            get("/comment-reactions") {
                val commentId = call.request.queryParameters["commentId"]?.let { UUID.fromString(it) } ?: throw IllegalArgumentException("Missing commentId parameter")
                val reactions = reactionService.getCommentReactions(viewer, commentId)
                call.respond(reactions)
            }
        }
    }
}
