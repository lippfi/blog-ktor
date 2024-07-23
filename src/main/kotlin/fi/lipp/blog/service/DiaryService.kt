package fi.lipp.blog.service

import java.net.URL

interface DiaryService {
    fun setDiaryStyle(userId: Long, styleContent: String)
    fun getDiaryStyle(diaryId: Long) : URL?
}