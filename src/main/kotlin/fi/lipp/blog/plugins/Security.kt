package fi.lipp.blog.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import fi.lipp.blog.data.UserPermission
import fi.lipp.blog.service.SessionService
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
import kotlin.time.Duration.Companion.minutes

const val USER_ID = "user-id"
const val SESSION_ID = "session-id"
const val PERMISSIONS = "permissions"
private val ACCESS_TOKEN_LIFETIME = 15.minutes.inWholeMilliseconds

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

                // 2) WebSocket
                call.request.queryParameters["token"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        val token = raw.removePrefix("Bearer ").trim()
                        return@authHeader HttpAuthHeader.Single("Bearer", token)
                    }

                null
            }

            validate { credential ->
                val userId = credential.payload.getClaim(USER_ID).asString()
                val sessionId = credential.payload.getClaim(SESSION_ID).asString()
                if (userId.isNullOrEmpty() || sessionId.isNullOrEmpty()) {
                    return@validate null
                }

                val sessionService by inject<SessionService>(SessionService::class.java)
                val sessionUUID = try {
                    UUID.fromString(sessionId)
                } catch (_: Exception) {
                    return@validate null
                }
                if (!sessionService.isSessionValid(sessionUUID)) {
                    return@validate null
                }

                JWTPrincipal(credential.payload)
            }
            
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

private val environment by inject<ApplicationEnvironment>(ApplicationEnvironment::class.java)

// Permissions in the access token are a snapshot at issuance time.
// Changes in DB take effect after the token is refreshed or reissued.
fun createAccessToken(userId: UUID, sessionId: UUID, permissions: Set<UserPermission> = emptySet()): String {
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtSecret = environment.config.property("jwt.secret").getString()

    val now = System.currentTimeMillis()
    val expiration = Date(now + ACCESS_TOKEN_LIFETIME)

    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .withClaim(USER_ID, userId.toString())
        .withClaim(SESSION_ID, sessionId.toString())
        .withClaim(PERMISSIONS, permissions.map { it.name })
        .withIssuedAt(Date(now))
        .withExpiresAt(expiration)
        .sign(Algorithm.HMAC256(jwtSecret))
}

inline val ApplicationCall.userId: UUID get() {
    val principal = this.principal<JWTPrincipal>()
        ?: throw fi.lipp.blog.model.exceptions.UnauthorizedException()
    val string = principal.payload.getClaim(USER_ID).asString()
        ?: throw fi.lipp.blog.model.exceptions.UnauthorizedException()
    return UUID.fromString(string)
}

inline val ApplicationCall.userPermissions: Set<UserPermission> get() {
    val principal = this.principal<JWTPrincipal>()
        ?: throw fi.lipp.blog.model.exceptions.UnauthorizedException()
    return principal.payload.getClaim(PERMISSIONS)
        ?.asList(String::class.java)
        ?.mapNotNull { raw -> runCatching { UserPermission.valueOf(raw) }.getOrNull() }
        ?.toSet()
        ?: emptySet()
}

inline val ApplicationCall.sessionId: UUID get() {
    val principal = this.principal<JWTPrincipal>()
        ?: throw fi.lipp.blog.model.exceptions.UnauthorizedException()
    val string = principal.payload.getClaim(SESSION_ID).asString()
        ?: throw fi.lipp.blog.model.exceptions.UnauthorizedException()
    return UUID.fromString(string)
}

inline val PipelineContext<*, ApplicationCall>.userId: UUID get() = call.userId
inline val PipelineContext<*, ApplicationCall>.sessionId: UUID get() = call.sessionId
inline val PipelineContext<*, ApplicationCall>.userPermissions: Set<UserPermission> get() = call.userPermissions

inline val ApplicationCall.viewer: Viewer
    get() {
    val principal = this.principal<JWTPrincipal>()
    if (principal == null) {
        val ip = this.request.origin.remoteHost
        val browserFingerprint = this.request.headers["User-Agent"] ?: "unknown"
        return Viewer.Anonymous(ip, browserFingerprint)
    }
    val userId = principal.payload.getClaim(USER_ID).asString()
    if (userId.isNullOrEmpty()) {
        val ip = this.request.origin.remoteHost
        val browserFingerprint = this.request.headers["User-Agent"] ?: "unknown"
        return Viewer.Anonymous(ip, browserFingerprint)
    }
    val permissions = principal.payload.getClaim(PERMISSIONS)
        ?.asList(String::class.java)
        ?.mapNotNull { raw -> runCatching { UserPermission.valueOf(raw) }.getOrNull() }
        ?.toSet()
        ?: emptySet()
    return Viewer.Registered(UUID.fromString(userId), permissions)
}

inline val PipelineContext<*, ApplicationCall>.viewer: Viewer get() = call.viewer
