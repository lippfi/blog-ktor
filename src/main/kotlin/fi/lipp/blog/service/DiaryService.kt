package fi.lipp.blog.service

import fi.lipp.blog.data.UserDto
import java.util.*

interface DiaryService {
    fun updateDiaryInfo(userId: UUID, diaryLogin: String, info: UserDto.DiaryInfo)
    
    fun setDiaryStyle(userId: UUID, styleContent: String)
    fun getDiaryStyle(diaryLogin: String): String
    fun getDiaryStyleFile(diaryLogin: String): String?

    fun updateDiaryName(userId: UUID, diaryLogin: String, name: String)
    fun updateDiarySubtitle(userId: UUID, diaryLogin: String, subtitle: String)
    fun updateDiaryDefaultReadGroup(userId: UUID, diaryLogin: String, groupId: UUID)
    fun updateDiaryDefaultCommentGroup(userId: UUID, diaryLogin: String, groupId: UUID)
    fun updateDiaryDefaultReactGroup(userId: UUID, diaryLogin: String, groupId: UUID)
}