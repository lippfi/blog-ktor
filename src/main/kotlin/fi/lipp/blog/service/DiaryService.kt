package fi.lipp.blog.service

import fi.lipp.blog.data.DiaryStyle
import fi.lipp.blog.data.DiaryStyleCreate
import fi.lipp.blog.data.DiaryStylePreview
import fi.lipp.blog.data.DiaryStyleUpdate
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import java.util.*

interface DiaryService {
    fun updateDiaryInfo(userId: UUID, diaryLogin: String, info: UserDto.DiaryInfo)

    fun getDiaryStyle(styleId: UUID): DiaryStylePreview
    fun getDiaryStyleText(styleId: UUID): String
    fun getEnabledDiaryStyles(diaryLogin: String): List<String>
    fun getDiaryStyleCollection(userId: UUID, diaryLogin: String): List<DiaryStyle>

    fun addDiaryStyle(userId: UUID, diaryLogin: String, styleId: UUID, enable: Boolean): DiaryStyle
    fun addDiaryStyle(userId: UUID, diaryLogin: String, style: DiaryStyleCreate): DiaryStyle
    fun addDiaryStyleWithFile(userId: UUID, diaryLogin: String, name: String, styleFile: FileUploadData, enabled: Boolean = true): DiaryStyle

    // when we update style, a new entity should be created
    fun updateDiaryStyle(userId: UUID, diaryLogin: String, update: DiaryStyleUpdate): DiaryStyle

    fun deleteDiaryStyle(userId: UUID, diaryLogin: String, styleId: UUID): Boolean

    fun reorderDiaryStyles(userId: UUID, diaryLogin: String, styleIds: List<UUID>): List<DiaryStyle>

    fun updateDiaryName(userId: UUID, diaryLogin: String, name: String)
    fun updateDiarySubtitle(userId: UUID, diaryLogin: String, subtitle: String)
    fun updateDiaryDefaultReadGroup(userId: UUID, diaryLogin: String, groupId: UUID)
    fun updateDiaryDefaultCommentGroup(userId: UUID, diaryLogin: String, groupId: UUID)
    fun updateDiaryDefaultReactGroup(userId: UUID, diaryLogin: String, groupId: UUID)
}
