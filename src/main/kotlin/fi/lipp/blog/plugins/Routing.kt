package fi.lipp.blog.plugins

import fi.lipp.blog.model.exceptions.BlogException
import fi.lipp.blog.routes.*
import fi.lipp.blog.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import java.util.UUID
import fi.lipp.blog.plugins.USER_ID

fun Application.configureRouting() {
    val userService by inject<UserService>()

    install(StatusPages) {
        exception<BlogException> { call, exception ->
            // Get the user's language preference if they are logged in
            val language = call.principal<JWTPrincipal>()?.let { principal ->
                val userId = principal.payload.getClaim(USER_ID).asString()
                if (userId.isNotEmpty()) {
                    userService.getUserLanguage(UUID.fromString(userId))
                } else null
            }

            val localizedMessage = exception.getLocalizedMessage(language)

            call.respondText(text = localizedMessage, status = HttpStatusCode.fromValue(exception.code))
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        userRoutes(get(), get())
        postRoutes(get(), get())
        diaryRoutes(get())
        storageRoutes(get())
        accessGroupRoutes(get())
        reactionRoutes(get())
        notificationRoutes(get())
        dialogRoutes(get())
        get("/") {
            call.respondText("Hello World!")
        }
    }
}