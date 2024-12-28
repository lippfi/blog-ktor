package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.util.UUID

class DiaryServiceImpl(private val storageService: StorageService) : DiaryService {
    override fun setDiaryStyle(userId: UUID, styleContent: String) {
        transaction {
            val styleUploadData = FileUploadData("style.css", styleContent.byteInputStream())
            val blogFile = storageService.store(userId, listOf(styleUploadData))[0]

            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.singleOrNull() ?: throw DiaryNotFoundException()
            diaryEntity.apply {
                style = EntityID(blogFile.id, Files)
            }
        }
    }

    override fun getDiaryStyle(diaryLogin: String): String? {
        val blogFile = getStyleFile(diaryLogin)
        return blogFile?.let { storageService.getFile(it).readText() }
    }

    override fun getDiaryStyleFile(diaryLogin: String): URL? {
        val blogFile = getStyleFile(diaryLogin)
        return blogFile?.let { storageService.getFileURL(it) }
    }

    private fun getStyleFile(diaryLogin: String): BlogFile? {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            val styleUUID = diaryEntity.style ?: return@transaction null
            FileEntity.findById(styleUUID)?.toBlogFile() ?: throw InternalServerError()
        }
    }
}