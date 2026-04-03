package fi.lipp.blog.routes

import fi.lipp.blog.data.*
import fi.lipp.blog.plugins.sessionId
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.plugins.viewer
import io.ktor.server.plugins.*
import fi.lipp.blog.service.GeoLocationService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.UserService
import fi.lipp.blog.service.Viewer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.*

fun Route.userRoutes(userService: UserService, reactionService: ReactionService, geoLocationService: GeoLocationService) {
    route("/user") {
        get("/search") {
            val text = call.request.queryParameters["text"] ?: ""
            val users = userService.search(text)
            call.respond(users)
        }

        get("/search-logins") {
            val names = (call.request.queryParameters["logins"] ?: "").split(",").map { it.trim() }
            val users = userService.getByLogins(names)
            call.respond(users)
        }

        post("/sign-up") {
            val user = call.receive<UserDto.Registration>()
            val inviteCode = call.request.queryParameters["invite-code"] ?: ""
            userService.signUp(user, inviteCode)
            call.respondText("Registration initiated. Please check your email to confirm your account.")
        }

        post("/confirm-registration") {
            val confirmationCode = call.request.queryParameters["code"] ?: ""
            val userAgent = call.request.headers["User-Agent"] ?: "unknown"
            val ip = call.request.origin.remoteHost
            val location = geoLocationService.resolveLocation(ip)
            val tokenPair = userService.confirmRegistration(confirmationCode, userAgent, location, userAgent)
            call.respond(tokenPair)
        }

        post("/sign-in") {
            val user = call.receive<UserDto.Login>()
            val userAgent = call.request.headers["User-Agent"] ?: "unknown"
            val ip = call.request.origin.remoteHost
            val location = geoLocationService.resolveLocation(ip)
            val tokenPair = userService.signIn(user, userAgent, location, userAgent)
            call.respond(tokenPair)
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
                val avatarUrls = userService.getAvatarUris(userId)
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
                val registeredViewer = viewer as Viewer.Registered
                val avatarUrls = userService.addAvatar(registeredViewer, files)
                call.respond(avatarUrls)
            }

            post("/add-avatar-by-uri") {
                val avatarUri = call.request.queryParameters["uri"]
                if (avatarUri == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing avatar uri query parameter")
                    return@post
                }
                userService.addAvatar(userId, avatarUri)
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

            get("/nickname") {
                val nickname = userService.getUserNickname(userId)
                call.respondText(nickname ?: "")
            }

            post("/update-nickname") {
                val nickname = call.receiveText()
                userService.updateNickname(userId, nickname)
                call.respondText("Nickname updated successfully")
            }

            get("/signature") {
                val signature = userService.getUserSignature(userId)
                call.respondText(signature ?: "")
            }

            post("/update-signature") {
                val signature = call.receiveText().takeIf { it.isNotBlank() }
                userService.updateSignature(userId, signature)
                call.respondText("Signature updated successfully")
            }

            post("/signature") {
                val signature = call.receiveText().takeIf { it.isNotBlank() }
                userService.updateSignature(userId, signature)
                call.respondText("Signature updated successfully")
            }

            get("/language") {
                val language = userService.getUserLanguage(userId)
                if (language != null) call.respond(language) else call.respondText("")
            }

            post("/update-language") {
                val language = call.receive<Language>()
                userService.updateLanguage(userId, language)
                call.respondText("Language updated successfully")
            }

            get("/timezone") {
                val timezone = userService.getUserTimezone(userId)
                call.respondText(timezone ?: "")
            }

            post("/update-timezone") {
                val timezone = call.receiveText()
                userService.updateTimezone(userId, timezone)
                call.respondText("Timezone updated successfully")
            }

            post("/update-password") {
                val request = call.receive<UpdatePasswordRequest>()
                userService.updatePassword(userId, request.newPassword, request.oldPassword)
                call.respondText("Password updated successfully")
            }

            get("/sex") {
                val sex = userService.getUserSex(userId)
                if (sex != null) call.respond(sex) else call.respondText("")
            }

            post("/update-sex") {
                val sex = call.receive<Sex>()
                userService.updateSex(userId, sex)
                call.respondText("Sex updated successfully")
            }

            get("/nsfw-policy") {
                val nsfwPolicy = userService.getUserNSFWPolicy(userId)
                if (nsfwPolicy != null) call.respond(nsfwPolicy) else call.respondText("")
            }

            post("/update-nsfw-policy") {
                val nsfwPolicy = call.receive<NSFWPolicy>()
                userService.updateNSFWPolicy(userId, nsfwPolicy)
                call.respondText("NSFW policy updated successfully")
            }

            get("/birthdate") {
                val birthDate = userService.getUserBirthDate(userId)
                if (birthDate != null) call.respond(birthDate) else call.respondText("")
            }

            post("/update-birthdate") {
                val birthDate = call.receive<LocalDate>()
                userService.updateBirthDate(userId, birthDate)
                call.respondText("Birth date updated successfully")
            }

            post("/do-not-show-in-feed") {
                val userLogin = call.parameters["login"] ?: throw IllegalArgumentException("Missing login parameter")
                userService.doNotShowInFeed(userId, userLogin)
                call.respondText("User hidden from feed successfully")
            }

            post("/show-in-feed") {
                val userLogin = call.parameters["login"] ?: throw IllegalArgumentException("Missing login parameter")
                userService.showInFeed(userId, userLogin)
                call.respondText("User shown in feed successfully")
            }

            get("/do-not-show-in-feed-list") {
                val hiddenUsers = userService.doNotShowInFeedList(userId)
                call.respond(hiddenUsers)
            }

            post("/ignore-user") {
                val userLogin = call.parameters["login"] ?: throw IllegalArgumentException("Missing login parameter")
                val reason = call.request.queryParameters["reason"]
                userService.ignoreUser(userId, userLogin, reason)
                call.respondText("User added to ignore list successfully")
            }

            post("/unignore-user") {
                val userLogin = call.parameters["login"] ?: throw IllegalArgumentException("Missing login parameter")
                userService.unignoreUser(userId, userLogin)
                call.respondText("User removed from ignore list successfully")
            }

            get("/ignored-users") {
                val ignoredUsers = userService.getIgnoredUsers(userId)
                call.respond(ignoredUsers)
            }
        }
    }
}

@Serializable
private data class UpdateUserRequest(
    val user: UserDto.Registration,
    val oldPassword: String
)

@Serializable
private data class UpdatePasswordRequest(
    val newPassword: String,
    val oldPassword: String
)
