package fi.lipp.blog.routes

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.PostService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.postRoutes(postService: PostService) {
    authenticate {
        route("/posts") {
            get("/preface") {
                val diaryId = UUID.fromString(call.parameters["diaryId"])
                val post = postService.getPreface(userId, diaryId)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound, "No preface found for diary $diaryId")
                } else {
                    call.respond(post)
                }
            }
            
            get("/") {
                // TODO getPost
            }
            
            get("/") {
                // TODO getPosts
            }
            
            get("/{postId}") {
                val postId = UUID.fromString(call.parameters["postId"])
                val post = postService.getPostForEdit(userId, postId)
                call.respond(post)
            }
            
            get("/{postId}") {
                val postId = UUID.fromString(call.parameters["postId"])
                val post = postService.getPostForEdit(userId, postId)
                call.respond(post)
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
