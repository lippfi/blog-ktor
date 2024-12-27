package fi.lipp.blog.plugins

import fi.lipp.blog.routes.postRoutes
import fi.lipp.blog.routes.userRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        userRoutes(get())
        postRoutes(get())
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
