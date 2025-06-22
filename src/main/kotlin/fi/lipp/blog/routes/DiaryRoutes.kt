package fi.lipp.blog.routes

import fi.lipp.blog.data.DiaryStyleCreate
import fi.lipp.blog.data.DiaryStyleUpdate
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.DiaryService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.diaryRoutes(diaryService: DiaryService) {
    route("/diary") {
        authenticate {
            post("/update-diary-info") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val info = call.receive<UserDto.DiaryInfo>()
                diaryService.updateDiaryInfo(userId, diaryLogin, info)
                call.respondText("Diary info updated successfully")
            }
        }

        // New routes for multiple styles
        route("/styles") {
            get {
                val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
                val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)
                call.respond(styles)
            }

            get("/{styleId}") {
                val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
                val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@get call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)
                val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)
                val style = styles.find { it.id == styleId }
                if (style != null) {
                    call.respond(style)
                } else {
                    call.respondText("Style not found", status = HttpStatusCode.NotFound)
                }
            }

            authenticate {
                post {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val style = call.receive<DiaryStyleCreate>()
                    val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, style)
                    call.respond(HttpStatusCode.Created, createdStyle)
                }

                post("/upload") {
                    val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
                    val name = call.request.queryParameters["name"] ?: "New Style"
                    val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true

                    val multipart = call.receiveMultipart()
                    var fileUploadData: FileUploadData? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val fileName = part.originalFileName ?: "style.css"
                            val stream = part.streamProvider.invoke()
                            fileUploadData = FileUploadData(fileName, stream)
                        }
                        part.dispose()
                    }

                    if (fileUploadData != null) {
                        val createdStyle = diaryService.addDiaryStyleWithFile(userId, diaryLogin, name, fileUploadData!!, enabled)
                        call.respond(HttpStatusCode.Created, createdStyle)
                    } else {
                        call.respondText("No file uploaded", status = HttpStatusCode.BadRequest)
                    }
                }

                put("/{styleId}") {
                    val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@put call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)
                    val update = call.receive<DiaryStyleUpdate>()
                    val updatedStyle = diaryService.updateDiaryStyle(userId, styleId, update)
                    if (updatedStyle != null) {
                        call.respond(updatedStyle)
                    } else {
                        call.respondText("Style not found", status = HttpStatusCode.NotFound)
                    }
                }

                put("/{styleId}/upload") {
                    val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@put call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)

                    val multipart = call.receiveMultipart()
                    var fileUploadData: FileUploadData? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val fileName = part.originalFileName ?: "style.css"
                            val stream = part.streamProvider.invoke()
                            fileUploadData = FileUploadData(fileName, stream)
                        }
                        part.dispose()
                    }

                    if (fileUploadData != null) {
                        val updatedStyle = diaryService.updateDiaryStyleWithFile(userId, styleId, fileUploadData!!)
                        call.respond(updatedStyle)
                    } else {
                        call.respondText("No file uploaded", status = HttpStatusCode.BadRequest)
                    }
                }

                put("/{styleId}/preview") {
                    val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@put call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)

                    val multipart = call.receiveMultipart()
                    var fileUploadData: FileUploadData? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val fileName = part.originalFileName ?: "preview.png"
                            val stream = part.streamProvider.invoke()
                            fileUploadData = FileUploadData(fileName, stream)
                        }
                        part.dispose()
                    }

                    if (fileUploadData != null) {
                        val updatedStyle = diaryService.updateDiaryStylePreview(userId, styleId, fileUploadData!!)
                        call.respond(updatedStyle)
                    } else {
                        call.respondText("No file uploaded", status = HttpStatusCode.BadRequest)
                    }
                }

                delete("/{styleId}") {
                    val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@delete call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)
                    val deleted = diaryService.deleteDiaryStyle(userId, styleId)
                    if (deleted) {
                        call.respondText("Style deleted successfully")
                    } else {
                        call.respondText("Style not found", status = HttpStatusCode.NotFound)
                    }
                }

                post("/reorder") {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val permutation = call.receive<List<String>>().map { UUID.fromString(it) }
                    val updatedStyles = diaryService.reorderDiaryStyles(userId, diaryLogin, permutation)
                    call.respond(updatedStyles)
                }
            }
        }
    }
}
