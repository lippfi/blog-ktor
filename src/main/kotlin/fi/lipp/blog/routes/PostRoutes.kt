package fi.lipp.blog.routes

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.plugins.USER_ID
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.Viewer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import java.util.*

fun Route.postRoutes(postService: PostService) {
    route("/posts") {
        authenticate(optional = true) {
            get("/preface") {
                val diaryLogin = call.request.queryParameters["diary"]
                if (diaryLogin == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing diary parameter")
                    return@get
                }
                val post = postService.getPreface(viewer, diaryLogin)
                if (post == null) {
                    call.respond(HttpStatusCode.NotFound, "No preface found")
                } else {
                    call.respond(post)
                }
            }

            get("/{authorLogin}/{uri}") {
                val authorLogin = call.parameters["authorLogin"]!!
                val uri = call.parameters["uri"]!!
                val post = postService.getPost(viewer, authorLogin, uri)
                call.respond(post)
            }
            
            get("/") {
                val author = call.request.queryParameters["author"]
                val diary = call.request.queryParameters["diary"]
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

                val posts = postService.getPosts(viewer, author, diary, text, tags?.let { Pair(tagPolicy, it) }, from, to, pageable)
                call.respond(posts)
            }
        }
        
        authenticate {
            get("/{postId}") {
                val postIdParameter = call.request.queryParameters["id"]
                if (postIdParameter == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing post id query parameter")
                    return@get
                }
                val postId = try {
                    UUID.fromString(postIdParameter)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid post id query parameter")
                    return@get
                }
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

private inline val PipelineContext<*, ApplicationCall>.viewer: Viewer get() {
    val principal = this.call.principal<JWTPrincipal>()
    if (principal == null) {
        val ip = this.call.request.origin.remoteHost
        val browserFingerprint = call.request.headers["User-Agent"] ?: "unknown"
        return Viewer.Anonymous(ip, browserFingerprint)
    }
    val userId = principal.payload.getClaim(USER_ID).asString()
    return Viewer.Registered(UUID.fromString(userId))
}
