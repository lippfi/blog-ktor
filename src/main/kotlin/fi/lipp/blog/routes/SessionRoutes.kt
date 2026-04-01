package fi.lipp.blog.routes

import fi.lipp.blog.data.RefreshRequest
import fi.lipp.blog.plugins.sessionId
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.SessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.sessionRoutes(sessionService: SessionService) {
    route("/session") {
        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val tokenPair = sessionService.refreshSession(request.refreshToken)
            call.respond(tokenPair)
        }

        authenticate {
            get("/active") {
                val sessions = sessionService.getActiveSessions(userId, sessionId)
                call.respond(sessions)
            }

            delete("/revoke/{sessionId}") {
                val targetSessionId = UUID.fromString(call.parameters["sessionId"])
                sessionService.revokeSession(userId, targetSessionId)
                call.respondText("Session revoked")
            }

            delete("/revoke-others") {
                sessionService.revokeOtherSessions(userId, sessionId)
                call.respondText("Other sessions revoked")
            }

            delete("/revoke-all") {
                sessionService.revokeAllSessions(userId)
                call.respondText("All sessions revoked")
            }
        }
    }
}
