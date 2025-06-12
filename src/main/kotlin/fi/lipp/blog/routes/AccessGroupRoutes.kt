package fi.lipp.blog.routes

import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.AccessGroupService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.accessGroupRoutes(accessGroupService: AccessGroupService) {
        route("/access-groups") {
            get("/basic") {
                val groups = accessGroupService.getBasicAccessGroups()
                call.respond(groups)
            }
            authenticate {
                get("/default") {
                    val diaryLogin = call.request.queryParameters["diary"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "Missing login"
                    )
                    val groups = accessGroupService.getDefaultAccessGroups(userId, diaryLogin)
                    call.respond(groups)
                }

            get {
                val diaryLogin = call.request.queryParameters["diary"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing login"
                )
                val groups = accessGroupService.getAccessGroups(userId, diaryLogin)
                call.respond(groups)
            }

            post {
                val diaryLogin = call.request.queryParameters["diary"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    "Missing diary login"
                )
                val groupName = call.receive<String>()
                accessGroupService.createAccessGroup(userId, diaryLogin, groupName)
                call.respondText("Access group created successfully")
            }

            delete {
                val groupId = UUID.fromString(call.request.queryParameters["groupId"])
                accessGroupService.deleteAccessGroup(userId, groupId)
                call.respondText("Access group deleted successfully")
            }

            post("/add-user") {
                val groupId = UUID.fromString(call.request.queryParameters["groupId"])
                val memberLogin = call.receive<String>()
                accessGroupService.addUserToGroup(userId, memberLogin, groupId)
                call.respondText("User added to group successfully")
            }

            post("/remove-user") {
                val groupId = UUID.fromString(call.request.queryParameters["groupId"])
                val memberLogin = call.receive<String>()
                accessGroupService.removeUserFromGroup(userId, memberLogin, groupId)
                call.respondText("User removed from group successfully")
            }
        }
    }
} 