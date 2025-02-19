package fi.lipp.blog.routes

import fi.lipp.blog.data.UserDto
import fi.lipp.blog.plugins.userId
import fi.lipp.blog.service.DiaryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.diaryRoutes(diaryService: DiaryService) {
    route("/diary") {
        get("/style-text") {
            val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
            val style = diaryService.getDiaryStyle(diaryLogin)
            call.respond(style)
        }

        get("/style-file") {
            val diaryLogin = call.request.queryParameters["diaryLogin"] ?: ""
            val styleFileUrl = diaryService.getDiaryStyleFile(diaryLogin)
            if (styleFileUrl != null) {
                call.respond(styleFileUrl)
            } else {
                call.respondText("Diary style file not found", status = HttpStatusCode.NotFound)
            }
        }
        
        authenticate {
            post("/set-style") {
                val styleContent = call.receive<String>()
                diaryService.setDiaryStyle(userId, styleContent)
                call.respondText("Diary style set successfully")
            }

            post("/update-diary-info") {
                val diaryLogin = call.request.queryParameters["login"] ?: ""
                val info = call.receive<UserDto.DiaryInfo>()
                diaryService.updateDiaryInfo(userId, diaryLogin, info)
                call.respondText("Diary info updated successfully")
            }
        }
    }
}
