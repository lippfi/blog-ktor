package fi.lipp.blog.routes

import fi.lipp.blog.data.FriendRequestDto
import fi.lipp.blog.data.NotificationSettings
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

fun Route.userRoutes(userService: UserService, reactionService: ReactionService) {
    route("/user") {
        post("/sign-up") {
            val user = call.receive<UserDto.Registration>()
            val inviteCode = call.request.queryParameters["invite-code"] ?: ""
            userService.signUp(user, inviteCode)
            call.respondText("Registration initiated. Please check your email to confirm your account.")
        }

        post("/confirm-registration") {
            val confirmationCode = call.request.queryParameters["code"] ?: ""
            val token = userService.confirmRegistration(confirmationCode)
            call.respondText(token)
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
            val nickname = call.request.queryParameters["nickname"] ?: ""
            val isBusy = userService.isNicknameBusy(nickname)
            call.respondText(isBusy.toString())
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

        authenticate {
            get("/session-info") {
                val sessionInfo = userService.getCurrentSessionInfo(userId)
                call.respond(sessionInfo)
            }

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

            get("/avatars") {
                val avatarUrls = userService.getAvatarUrls(userId)
                call.respond(avatarUrls)
            }

            post("/reorder-avatars") {
                val permutation = call.receive<List<String>>().map { UUID.fromString(it) }
                userService.reorderAvatars(userId, permutation)
                call.respondText("Avatars reordered successfully")
            }

            post("/add-avatar") {
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                val avatarUrls = userService.addAvatar(userId, files)
                call.respond(avatarUrls)
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

            post("/notification-settings") {
                val settings = call.receive<NotificationSettings>()
                userService.updateNotificationSettings(userId, settings)
                call.respondText("Notification settings updated successfully")
            }

            post("/friend-request") {
                val request = call.receive<FriendRequestDto.Create>()
                userService.sendFriendRequest(userId, request)
                call.respondText("Friend request sent successfully")
            }

            post("/friend-request/accept") {
                val requestId = UUID.fromString(call.request.queryParameters["requestId"]!!)
                val label = call.request.queryParameters["label"]
                userService.acceptFriendRequest(userId, requestId, label)
                call.respondText("Friend request accepted")
            }

            post("/friend-request/decline") {
                val requestId = UUID.fromString(call.request.queryParameters["requestId"]!!)
                userService.declineFriendRequest(userId, requestId)
                call.respondText("Friend request declined")
            }

            delete("/friend-request") {
                val requestId = UUID.fromString(call.request.queryParameters["requestId"]!!)
                userService.cancelFriendRequest(userId, requestId)
                call.respondText("Friend request cancelled")
            }

            get("/friend-requests/sent") {
                val requests = userService.getSentFriendRequests(userId)
                call.respond(requests)
            }

            get("/friend-requests/received") {
                val requests = userService.getReceivedFriendRequests(userId)
                call.respond(requests)
            }

            get("/friends") {
                val friends = userService.getFriends(userId)
                call.respond(friends)
            }

            delete("/friends") {
                val friendLogin = call.parameters["login"] ?: throw IllegalArgumentException("Missing friendLogin parameter")
                userService.removeFriend(userId, friendLogin)
                call.respondText("Friend removed successfully")
            }
        }
    }
}

@Serializable
private data class UpdateUserRequest(
    val user: UserDto.Registration,
    val oldPassword: String
)
