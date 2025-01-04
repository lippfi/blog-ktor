package fi.lipp.blog.routes

import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.StorageService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.storageRoutes(storageService: StorageService) {
    authenticate {
        route("/storage") {
            post("/upload") {
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                val storedFiles = storageService.store(userId, files)
                call.respond(storedFiles)
            }
        }
    }
} 