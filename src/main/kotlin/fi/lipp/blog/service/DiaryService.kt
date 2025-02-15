package fi.lipp.blog.service

import fi.lipp.blog.data.UserDto
import java.util.*

interface DiaryService {
    fun updateDiaryInfo(userId: UUID, diaryLogin: String, info: UserDto.DiaryInfo)
    
    fun setDiaryStyle(userId: UUID, styleContent: String)
    fun getDiaryStyle(diaryLogin: String): String
    fun getDiaryStyleFile(diaryLogin: String): String?
}