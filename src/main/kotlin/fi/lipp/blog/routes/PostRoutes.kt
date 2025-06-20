package fi.lipp.blog.routes

import fi.lipp.blog.data.*
import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.plugins.viewer
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.SortOrder
import java.util.*

fun Route.postRoutes(postService: PostService, reactionService: ReactionService) {
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

            get {
                val diaryLogin = call.request.queryParameters["login"]!!
                val uri = call.request.queryParameters["uri"]!!
                val post = postService.getPost(viewer, diaryLogin, uri)
                call.respond(post)
            }

            get("/diary") {
                val diaryLogin = call.request.queryParameters["diary"]
                if (diaryLogin == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing diary parameter")
                    return@get
                }
                val text = call.request.queryParameters["text"]
                val tags = call.request.queryParameters["tags"]?.split(",")?.toSet()
                val from = call.request.queryParameters["from"]?.let { LocalDate.parse(it) }
                val to = call.request.queryParameters["to"]?.let { LocalDate.parse(it) }
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = if (call.request.queryParameters["sort"]?.lowercase() == "asc") SortOrder.ASC else SortOrder.DESC,
                )
                val tagPolicy = TagPolicy.valueOf(call.request.queryParameters["tagPolicy"] ?: "UNION")

                val posts = postService.getDiaryPosts(viewer, diaryLogin, text, tags?.let { Pair(tagPolicy, it) }, from, to, pageable)
                call.respond(posts)
            }

            get("/search") {
                val author = call.request.queryParameters["author"]
                val diary = call.request.queryParameters["diary"]
                val text = call.request.queryParameters["text"]
                val tags = call.request.queryParameters["tags"]?.split(",")?.toSet()
                val from = call.request.queryParameters["from"]?.let { LocalDate.parse(it) }
                val to = call.request.queryParameters["to"]?.let { LocalDate.parse(it) }
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = if (call.request.queryParameters["sort"]?.lowercase() == "asc") SortOrder.ASC else SortOrder.DESC,
                )
                val tagPolicy = TagPolicy.valueOf(call.request.queryParameters["tagPolicy"] ?: "UNION")

                val posts = postService.getPosts(viewer, author, diary, text, tags?.let { Pair(tagPolicy, it) }, from, to, pageable)
                call.respond(posts)
            }

            get {
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = SortOrder.DESC,
                )
                val posts = postService.getPosts(viewer, pageable)
                call.respond(posts)
            }

            get("/discussed") {
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = SortOrder.DESC,
                )
                val posts = postService.getDiscussedPosts(viewer, pageable)
                call.respond(posts)
            }

            get("/comment") {
                val commentId = UUID.fromString(call.request.queryParameters["commentId"])
                val comment = postService.getComment(viewer, commentId)
                call.respond(comment)
            }
        }

        authenticate {
            get("/followed") {
                val pageable = Pageable(
                    page = call.request.queryParameters["page"]?.toInt() ?: 0,
                    size = call.request.queryParameters["size"]?.toInt() ?: 10,
                    direction = SortOrder.DESC,
                )
                val posts = postService.getFollowedPosts(userId, pageable)
                call.respond(posts)
            }

            get {
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

            put {
                val post = call.receive<PostDto.Update>()
                val updatedPost = postService.updatePost(userId, post)
                call.respond(updatedPost)
            }

            delete {
                val postId = UUID.fromString(call.request.queryParameters["postId"])
                postService.deletePost(userId, postId)
                call.respondText("Post deleted successfully")
            }

            post("/comment") {
                val comment = call.receive<CommentDto.Create>()
                val addedComment = postService.addComment(userId, comment)
                call.respond(addedComment)
            }

            put("/comment") {
                val comment = call.receive<CommentDto.Update>()
                val updatedComment = postService.updateComment(userId, comment)
                call.respond(updatedComment)
            }

            delete("/comment") {
                val commentId = UUID.fromString(call.request.queryParameters["commentId"])
                postService.deleteComment(userId, commentId)
                call.respondText("Comment deleted successfully")
            }

            post {
                val post = call.receive<PostDto.Create>()
                val createdPost = postService.addPost(userId, post)
                call.respond(createdPost)
            }
        }
    }
}
