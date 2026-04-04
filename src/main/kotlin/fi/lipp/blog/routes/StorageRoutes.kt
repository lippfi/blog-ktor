package fi.lipp.blog.routes

import fi.lipp.blog.data.toFileUploadDatas
import fi.lipp.blog.plugins.viewer
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
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
                val registeredViewer = viewer as Viewer.Registered
                val storedFiles = storageService.store(registeredViewer, files).map { storageService.getFileURL(it) }
                call.respond(storedFiles)
            }

            post("/upload-avatars") {
                val multipart = call.receiveMultipart()
                val files = multipart.toFileUploadDatas()
                val registeredViewer = viewer as Viewer.Registered
                val storedFiles = storageService.storeAvatars(registeredViewer, files).map { storageService.getFileURL(it) }
                call.respond(storedFiles)
            }
        }
    }
}
