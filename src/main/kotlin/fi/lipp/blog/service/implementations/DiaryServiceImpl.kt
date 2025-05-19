package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.model.exceptions.InvalidAccessGroupException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.AccessGroups
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class DiaryServiceImpl(private val storageService: StorageService) : DiaryService {
    override fun updateDiaryInfo(userId: UUID, diaryLogin: String, info: UserDto.DiaryInfo) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            // Check if the access groups exist and are associated with this diary or are global
            val readGroup = AccessGroupEntity.findById(info.defaultReadGroup) ?: throw InvalidAccessGroupException()
            if (readGroup.diaryId != null && readGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()
            val commentGroup = AccessGroupEntity.findById(info.defaultCommentGroup) ?: throw InvalidAccessGroupException()
            if (commentGroup.diaryId != null && commentGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()
            val reactGroup = AccessGroupEntity.findById(info.defaultReactGroup) ?: throw InvalidAccessGroupException()
            if (reactGroup.diaryId != null && reactGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()

            diaryEntity.apply { 
                name = info.name
                subtitle = info.subtitle
                defaultReadGroup = readGroup.id
                defaultCommentGroup = commentGroup.id
                defaultReactGroup = reactGroup.id
            }
        }
    }

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

    override fun getDiaryStyle(diaryLogin: String): String {
        val blogFile = getStyleFile(diaryLogin)
        return blogFile?.let { storageService.getFile(it).readText() } ?: ""
    }

    override fun getDiaryStyleFile(diaryLogin: String): String? {
        val blogFile = getStyleFile(diaryLogin)
        return blogFile?.let { storageService.getFileURL(it) }
    }

    override fun updateDiaryName(userId: UUID, diaryLogin: String, name: String) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            diaryEntity.apply {
                this.name = name
            }
        }
    }

    override fun updateDiarySubtitle(userId: UUID, diaryLogin: String, subtitle: String) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            diaryEntity.apply {
                this.subtitle = subtitle
            }
        }
    }

    override fun updateDiaryDefaultReadGroup(userId: UUID, diaryLogin: String, groupId: UUID) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val readGroup = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
            if (readGroup.diaryId != null && readGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()

            diaryEntity.apply {
                defaultReadGroup = readGroup.id
            }
        }
    }

    override fun updateDiaryDefaultCommentGroup(userId: UUID, diaryLogin: String, groupId: UUID) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val commentGroup = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
            if (commentGroup.diaryId != null && commentGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()

            diaryEntity.apply {
                defaultCommentGroup = commentGroup.id
            }
        }
    }

    override fun updateDiaryDefaultReactGroup(userId: UUID, diaryLogin: String, groupId: UUID) {
        transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val reactGroup = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
            if (reactGroup.diaryId != null && reactGroup.diaryId != diaryEntity.id) throw InvalidAccessGroupException()

            diaryEntity.apply {
                defaultReactGroup = reactGroup.id
            }
        }
    }

    private fun getStyleFile(diaryLogin: String): BlogFile? {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            val styleUUID = diaryEntity.style ?: return@transaction null
            FileEntity.findById(styleUUID)?.toBlogFile() ?: throw InternalServerError()
        }
    }
}
