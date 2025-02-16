package fi.lipp.blog.plugins

import fi.lipp.blog.model.exceptions.BlogException
import fi.lipp.blog.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.configureRouting() {
    install(StatusPages) {
        exception<BlogException> { call, exception ->
            call.respondText(text = exception.message, status = HttpStatusCode.fromValue(exception.code))
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
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
