package fi.lipp.blog.routes

import fi.lipp.blog.data.UserDto
import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

fun Route.userRoutes(userService: UserService) {
    route("/user") {
        post("/sign-up") {
            val user = call.receive<UserDto.Registration>()
            val inviteCode = call.request.queryParameters["invite-code"] ?: ""
            userService.signUp(user, inviteCode)
            call.respondText("User signed up successfully")
        }

        post("/sign-in") {
            val user = call.receive<UserDto.Login>()
            val token = userService.signIn(user)
            call.respondText(token)
        }

        get("/is-login-busy") {
            val login = call.request.queryParameters["login"] ?: ""
            val isBusy = userService.isLoginBusy(login)
            call.respondText(isBusy.toString())
        }

        get("/is-email-busy") {
            val email = call.request.queryParameters["email"] ?: ""
            val isBusy = userService.isEmailBusy(email)
            call.respondText(isBusy.toString())
        }

        get("/is-nickname-busy") {
            val email = call.request.queryParameters["nickname"] ?: ""
            val isBusy = userService.isEmailBusy(email)
            call.respondText(isBusy.toString())
        }

        authenticate {
            get("/create-invite-code") {
                val code = userService.generateInviteCode(userId)
                call.respondText(code)
            }
            
            post("/update") {
                val (user, oldPassword) = call.receive<UpdateUserRequest>()
                userService.update(userId, user, oldPassword)
                call.respondText("User updated successfully")
            }
            
            post("/update-additional-info") {
                val info = call.receive<UserDto.AdditionalInfo>()
                userService.updateAdditionalInfo(userId, info)
                call.respondText("User info updated successfully")
            }

            post("/send-password-reset-email") {
                val userIdentifier = call.receive<String>()
                userService.sendPasswordResetEmail(userIdentifier)
                call.respondText("Password reset email sent")
            }

            post("/reset-password") {
                val resetCode = call.request.queryParameters["code"] ?: ""
                val newPassword = call.receive<String>()
                userService.performPasswordReset(resetCode, newPassword)
                call.respondText("Password reset successfully")
            }

            get("/avatars") {
                val avatarUrls = userService.getAvatarUrls(userId)
                call.respond(avatarUrls)
            }

            post("/reorder-avatars") {
                val permutation = call.receive<List<UUID>>()
                userService.reorderAvatars(userId, permutation)
                call.respondText("Avatars reordered successfully")
            }

            post("/add-avatar") {
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                userService.addAvatar(userId, files)
                call.respondText("Avatar added successfully")
            }

            delete("/delete-avatar") {
                val avatarUri = call.request.queryParameters["uri"]
                if (avatarUri == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing avatar uri query parameter")
                    return@delete
                }
                userService.deleteAvatar(userId, avatarUri)
                call.respondText("Avatar deleted successfully")
            }
        }
    }
}

@Serializable
private data class UpdateUserRequest(
    val user: UserDto.Registration,
    val oldPassword: String
)
