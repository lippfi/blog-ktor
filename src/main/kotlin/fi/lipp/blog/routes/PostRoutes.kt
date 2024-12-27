package fi.lipp.blog.routes

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.PostService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import java.util.*

fun Route.postRoutes(postService: PostService) {
    authenticate {
        route("/posts") {
            get("/preface") {
                val diaryId = UUID.fromString(call.request.queryParameters["diaryId"])
                val post = postService.getPreface(userId, diaryId)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound, "No preface found for diary $diaryId")
                } else {
                    call.respond(post)
                }
            }
            
            get("/{postId}") {
                val postId = UUID.fromString(call.parameters["postId"])
                val post = postService.getPostForEdit(userId, postId)
                call.respond(post)
            }

            get("/{authorLogin}/{uri}") {
                val authorLogin = call.parameters["authorLogin"]!!
                val uri = call.parameters["uri"]!!
                val post = postService.getPost(userId, authorLogin, uri)
                call.respond(post)
            }
            
            get("/") {
                val authorId = call.request.queryParameters["authorId"]?.let { UUID.fromString(it) }
                val diaryId = call.request.queryParameters["diaryId"]?.let { UUID.fromString(it) }
                val text = call.request.queryParameters["text"]
                val tags = call.request.queryParameters["tags"]?.split(",")?.toSet()
                val from = call.request.queryParameters["from"]?.let { LocalDateTime.parse(it) }
                val to = call.request.queryParameters["to"]?.let { LocalDateTime.parse(it) }
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = SortOrder.DESC,
                )
                val tagPolicy = TagPolicy.valueOf(call.request.queryParameters["tagPolicy"] ?: "UNION")

                val posts = postService.getPosts(userId, authorId, diaryId, text, tags?.let { Pair(tagPolicy, it) }, from, to, pageable)
                call.respond(posts)
            }

            put("/{postId}") {
                val post = call.receive<PostDto.Update>()
                postService.updatePost(userId, post)
                call.respondText("Post updated successfully")
            }

            delete("/{postId}") {
                val postId = UUID.fromString(call.parameters["postId"])
                postService.deletePost(userId, postId)
                call.respondText("Post deleted successfully")
            }

            post("/{postId}/comments") {
                val comment = call.receive<CommentDto.Create>()
                postService.addComment(userId, comment)
                call.respondText("Comment added successfully")
            }

            put("/comments/{commentId}") {
                val comment = call.receive<CommentDto.Update>()
                postService.updateComment(userId, comment)
                call.respondText("Comment updated successfully")
            }

            delete("/comments/{commentId}") {
                val commentId = UUID.fromString(call.parameters["commentId"])
                postService.deleteComment(userId, commentId)
                call.respondText("Comment deleted successfully")
            }
        }
    }
}
