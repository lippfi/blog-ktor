package fi.lipp.blog.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import fi.lipp.blog.service.Viewer
import io.ktor.http.*
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import org.koin.java.KoinJavaComponent.inject
import java.util.*

const val USER_ID = "user-id"
private const val TOKEN_LIFETIME = 365 * 24 * 60 * 60 * 1000

fun Application.configureSecurity() {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()
    val jwtSecret = environment.config.property("jwt.secret").getString()
    authentication {
        jwt {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )

            authHeader { call ->
                // 1) Normal HTTP: Authorization: Bearer <token>
                val h = call.request.parseAuthorizationHeader()
                if (h != null) return@authHeader h

                // 2) Browser WebSocket: token in Sec-WebSocket-Protocol
                val proto = call.request.headers["Sec-WebSocket-Protocol"] ?: return@authHeader null
                val token = proto.split(',')
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("bearer.") }
                    ?.removePrefix("bearer.")
                    ?: return@authHeader null

                HttpAuthHeader.Single("Bearer", token)
            }

            validate { credential ->
                if (credential.payload.getClaim(USER_ID).asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

private val environment by inject<ApplicationEnvironment>(ApplicationEnvironment::class.java)

fun createJwtToken(userId: UUID): String {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtSecret = environment.config.property("jwt.secret").getString()

    val now = System.currentTimeMillis()
    val expiration = Date(now + TOKEN_LIFETIME)

    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .withClaim(USER_ID, userId.toString())
        .withExpiresAt(expiration)
        .sign(Algorithm.HMAC256(jwtSecret))
}

inline val ApplicationCall.userId: UUID get() {
    val string = this.principal<JWTPrincipal>()!!.payload.getClaim(USER_ID).asString()
    return UUID.fromString(string)
}

inline val PipelineContext<*, ApplicationCall>.userId: UUID get() = call.userId

inline val ApplicationCall.viewer: Viewer
    get() {
    val principal = this.principal<JWTPrincipal>()
    if (principal == null) {
        val ip = this.request.origin.remoteHost
        val browserFingerprint = this.request.headers["User-Agent"] ?: "unknown"
        return Viewer.Anonymous(ip, browserFingerprint)
    }
    val userId = principal.payload.getClaim(USER_ID).asString()
    return Viewer.Registered(UUID.fromString(userId))
}

inline val PipelineContext<*, ApplicationCall>.viewer: Viewer get() = call.viewer
