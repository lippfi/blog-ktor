package fi.lipp.blog.routes

import fi.lipp.blog.data.DiaryStyleCreate
import fi.lipp.blog.data.DiaryStyleUpdate
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.util.UUIDSerializer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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

            post("/update-profile") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val profileContent = call.receiveText()
                diaryService.updateProfileContent(userId, diaryLogin, profileContent)
                call.respondText("Profile content updated successfully")
            }

            post("/update-title-subtitle") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val request = call.receive<UpdateTitleSubtitleRequest>()
                diaryService.updateDiaryName(userId, diaryLogin, request.title)
                diaryService.updateDiarySubtitle(userId, diaryLogin, request.subtitle)
                call.respondText("Diary title and subtitle updated successfully")
            }

            post("/update-default-groups") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val groups = call.receive<UpdateDefaultGroupsRequest>()
                diaryService.updateDiaryDefaultReadGroup(userId, diaryLogin, groups.defaultReadGroup)
                diaryService.updateDiaryDefaultCommentGroup(userId, diaryLogin, groups.defaultCommentGroup)
                diaryService.updateDiaryDefaultReactGroup(userId, diaryLogin, groups.defaultReactGroup)
                call.respondText("Diary default groups updated successfully")
            }
        }

        get("/profile") {
            val diaryLogin = call.request.queryParameters["login"] ?: ""
            val userProfilePage = diaryService.getUserProfilePage(diaryLogin)
            call.respond(userProfilePage)
        }

        // New routes for multiple styles
        route("/styles") {
            // Public route to get enabled diary styles
            get("/enabled") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val styles = diaryService.getEnabledDiaryStyles(diaryLogin)
                call.respond(styles)
            }

            // Public route to get a specific style by ID
            get("/{styleId}") {
                val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } 
                    ?: return@get call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)
                val style = diaryService.getDiaryStyle(styleId)
                call.respond(style)
            }

            authenticate {
                get {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)
                    call.respond(styles)
                }

                post {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val style = call.receive<DiaryStyleCreate>()
                    val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, style)
                    call.respond(HttpStatusCode.Created, createdStyle)
                }

                post("/add-style-by-id") {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val styleId = call.request.queryParameters["styleId"]?.let { UUID.fromString(it) } 
                        ?: return@post call.respondText("Missing or invalid styleId parameter", status = HttpStatusCode.BadRequest)
                    val enable = call.request.queryParameters["enable"]?.toBoolean() ?: false

                    val updatedStyle = diaryService.addDiaryStyle(userId, diaryLogin, styleId, enable)
                    call.respond(HttpStatusCode.Created, updatedStyle)
                }

                post("/upload") {
                    val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
                    val name = call.request.queryParameters["name"] ?: "New Style"
                    val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true

                    val multipart = call.receiveMultipart()
                    var fileUploadData: FileUploadData? = null

                    multipart.forEachPart { part ->
                        try {
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName ?: "style.css"
                                val bytes = part.streamProvider().readBytes()
                                fileUploadData = FileUploadData(fileName, bytes)
                            }
                        } finally {
                            part.dispose()
                        }
                    }

                    if (fileUploadData != null) {
                        val createdStyle = diaryService.addDiaryStyleWithFile(userId, diaryLogin, name, fileUploadData!!, enabled)
                        call.respond(HttpStatusCode.Created, createdStyle)
                    } else {
                        call.respondText("No file uploaded", status = HttpStatusCode.BadRequest)
                    }
                }

                put("/{styleId}") {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val update = call.receive<DiaryStyleUpdate>()
                    val updatedStyle = diaryService.updateDiaryStyle(userId, diaryLogin, update)
                    call.respond(updatedStyle)
                }

                delete("/{styleId}") {
                    val diaryLogin = call.request.queryParameters["login"] ?: ""
                    val styleId = call.parameters["styleId"]?.let { UUID.fromString(it) } ?: return@delete call.respondText("Invalid style ID", status = HttpStatusCode.BadRequest)
                    val deleted = diaryService.deleteDiaryStyle(userId, diaryLogin, styleId)
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

@Serializable
private data class UpdateTitleSubtitleRequest(
    val title: String,
    val subtitle: String,
)

@Serializable
private data class UpdateDefaultGroupsRequest(
    @Serializable(with = UUIDSerializer::class)
    val defaultReadGroup: UUID,
    @Serializable(with = UUIDSerializer::class)
    val defaultCommentGroup: UUID,
    @Serializable(with = UUIDSerializer::class)
    val defaultReactGroup: UUID,
)
