package fi.lipp.blog.plugins

import fi.lipp.blog.util.URLSerializer
import fi.lipp.blog.util.UUIDSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.net.URL
import java.util.UUID

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            serializersModule = SerializersModule {
                contextual(URL::class, URLSerializer)
                contextual(UUID::class, UUIDSerializer)
            }
        })
    }
    routing {
        get("/json/kotlinx-serialization") {
                call.respond(mapOf("hello" to "world"))
            }
    }
}