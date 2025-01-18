package fi.lipp.blog.service

import java.util.*

interface DiaryService {
    fun setDiaryStyle(userId: UUID, styleContent: String)
    fun getDiaryStyle(diaryLogin: String): String
    fun getDiaryStyleFile(diaryLogin: String): String?
}