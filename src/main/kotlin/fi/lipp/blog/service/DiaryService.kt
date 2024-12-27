package fi.lipp.blog.service

import java.net.URL
import java.util.*

interface DiaryService {
    fun setDiaryStyle(userId: UUID, styleContent: String)
    fun getDiaryStyle(diaryId: UUID) : URL?
}