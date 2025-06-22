package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.DiaryStyleEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.DiaryStyles
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.service.StorageService
import org.jetbrains.exposed.sql.SortOrder
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

    override fun getDiaryStyleText(styleId: UUID): String {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()
            val styleFile = styleEntity.styleFile.toBlogFile()
            val file = storageService.getFile(styleFile)
            file.readText()
        }
    }

    override fun getEnabledDiaryStyles(diaryLogin: String): List<String> {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            DiaryStyleEntity.find { (DiaryStyles.diary eq diaryEntity.id) and (DiaryStyles.enabled eq true) }
                .orderBy(DiaryStyles.ordinal to SortOrder.ASC)
                .map { styleEntity ->
                    storageService.getFileURL(styleEntity.styleFile.toBlogFile())
                }
        }
    }

    override fun getDiaryStyleCollection(userId: UUID, diaryLogin: String): List<DiaryStyle> {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            DiaryStyleEntity.find { DiaryStyles.diary eq diaryEntity.id }
                .orderBy(DiaryStyles.ordinal to SortOrder.ASC)
                .map { styleEntity ->
                    DiaryStyle(
                        id = styleEntity.id.value,
                        name = styleEntity.name,
                        enabled = styleEntity.enabled,
                        styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                        previewPictureUri = styleEntity.previewPictureUri
                    )
                }
        }
    }

    // TODO utilize preview picture
    override fun addDiaryStyle(userId: UUID, diaryLogin: String, style: DiaryStyleCreate): DiaryStyle {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val styleUploadData = FileUploadData("style.css", style.styleContent.byteInputStream())
            val blogFile = storageService.store(userId, listOf(styleUploadData))[0]

            val maxOrdinal = DiaryStyleEntity.find { DiaryStyles.diary eq diaryEntity.id }
                .maxOfOrNull { it.ordinal } ?: -1

            val styleEntity = DiaryStyleEntity.new {
                name = style.name
                ordinal = maxOrdinal + 1
                enabled = style.enabled
                diary = diaryEntity
                styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()
                previewPictureUri = style.previewPictureUri
            }

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = styleEntity.enabled,
                styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun addDiaryStyleWithFile(userId: UUID, diaryLogin: String, name: String, styleFile: FileUploadData, enabled: Boolean): DiaryStyle {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val blogFile = storageService.store(userId, listOf(styleFile))[0]

            val maxOrdinal = DiaryStyleEntity.find { DiaryStyles.diary eq diaryEntity.id }
                .maxOfOrNull { it.ordinal } ?: -1

            val styleEntity = DiaryStyleEntity.new {
                this.name = name
                this.ordinal = maxOrdinal + 1
                this.enabled = enabled
                this.diary = diaryEntity
                this.styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()
            }

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = styleEntity.enabled,
                styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun updateDiaryStyle(userId: UUID, styleId: UUID, update: DiaryStyleUpdate): DiaryStyle {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId)!!
            if (styleEntity.diary.owner.value != userId) throw WrongUserException()

            styleEntity.name = update.name
            styleEntity.enabled = update.enabled
            styleEntity.previewPictureUri = update.previewPictureUri

            val styleUploadData = FileUploadData("style.css", update.styleContent.byteInputStream())
            val blogFile = storageService.store(userId, listOf(styleUploadData))[0]
            styleEntity.styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = styleEntity.enabled,
                styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri,
            )
        }
    }

    override fun updateDiaryStyleWithFile(userId: UUID, styleId: UUID, styleFile: FileUploadData): DiaryStyle {
        return transaction {

            val styleEntity = DiaryStyleEntity.findById(styleId)!!
            if (styleEntity.diary.owner.value != userId) throw WrongUserException()

            val blogFile = storageService.store(userId, listOf(styleFile))[0]
            styleEntity.styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = styleEntity.enabled,
                styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun updateDiaryStylePreview(userId: UUID, styleId: UUID, previewFile: FileUploadData): DiaryStyle {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId)!!
            if (styleEntity.diary.owner.value != userId) throw WrongUserException()

            val blogFile = storageService.store(userId, listOf(previewFile))[0]

            styleEntity.previewPictureUri = storageService.getFileURL(blogFile)

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = styleEntity.enabled,
                styleFileUrl = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun deleteDiaryStyle(userId: UUID, styleId: UUID): Boolean {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId)!!
            if (styleEntity.diary.owner.value != userId) throw WrongUserException()

            styleEntity.delete()
            true
        }
    }

    override fun reorderDiaryStyles(userId: UUID, diaryLogin: String, styleIds: List<UUID>): List<DiaryStyle> {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val styles = DiaryStyleEntity.find { DiaryStyles.diary eq diaryEntity.id }.toList()

            val styleMap = styles.associateBy { it.id.value }
            for (styleId in styleIds) {
                if (!styleMap.containsKey(styleId)) throw InvalidStyleException()
            }

            if (styleIds.size != styles.size) throw InvalidStyleException()

            for ((index, styleId) in styleIds.withIndex()) {
                styleMap[styleId]?.ordinal = index
            }

            getDiaryStyleCollection(userId, diaryLogin)
        }
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
}
