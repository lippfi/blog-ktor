package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.DiaryStyleEntity
import fi.lipp.blog.domain.DiaryStyleJunctionEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.DiaryStyleJunctions
import fi.lipp.blog.service.DiaryService
import fi.lipp.blog.service.StorageService
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

            // Get all junction entities for this diary where enabled is true, ordered by ordinal
            diaryEntity.styleJunctions
                .filter { it.enabled }
                .sortedBy { it.ordinal }
                .map { junction ->
                    storageService.getFileURL(junction.style.styleFile.toBlogFile())
                }
        }
    }

    override fun getDiaryStyleCollection(userId: UUID, diaryLogin: String): List<DiaryStyle> {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            // Get all junction entities for this diary, ordered by ordinal
            diaryEntity.styleJunctions
                .sortedBy { it.ordinal }
                .map { junction ->
                    DiaryStyle(
                        id = junction.style.id.value,
                        name = junction.style.name,
                        enabled = junction.enabled,
                        styleFileUri = storageService.getFileURL(junction.style.styleFile.toBlogFile()),
                        previewPictureUri = junction.style.previewPictureUri
                    )
                }
        }
    }

    override fun addDiaryStyle(userId: UUID, diaryLogin: String, styleId: UUID, enable: Boolean): DiaryStyle {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()

            // Check if the diary already has this style
            val existingJunction = DiaryStyleJunctionEntity.find { 
                (DiaryStyleJunctions.diary eq diaryEntity.id) and 
                (DiaryStyleJunctions.style eq styleEntity.id) 
            }.singleOrNull()

            if (existingJunction != null) {
                // Style already exists for this diary, return it
                DiaryStyle(
                    id = styleEntity.id.value,
                    name = styleEntity.name,
                    enabled = existingJunction.enabled,
                    styleFileUri = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                    previewPictureUri = styleEntity.previewPictureUri
                )
            } else {
                val maxOrdinal = diaryEntity.styleJunctions.maxOfOrNull { it.ordinal } ?: -1

                val junction = DiaryStyleJunctionEntity.new {
                    diary = diaryEntity
                    style = styleEntity
                    ordinal = maxOrdinal + 1
                    enabled = enable
                }

                DiaryStyle(
                    id = styleEntity.id.value,
                    name = styleEntity.name,
                    enabled = junction.enabled,
                    styleFileUri = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                    previewPictureUri = styleEntity.previewPictureUri
                )
            }
        }
    }

    override fun addDiaryStyle(userId: UUID, diaryLogin: String, style: DiaryStyleCreate): DiaryStyle {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val styleUploadData = FileUploadData("style.css", style.styleContent.byteInputStream())
            val blogFile = storageService.store(userId, listOf(styleUploadData))[0]

            val styleEntity = DiaryStyleEntity.new {
                name = style.name
                styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()
                previewPictureUri = style.previewPictureUri
            }

            // Create the junction entity to link the diary and style
            val maxOrdinal = diaryEntity.styleJunctions.maxOfOrNull { it.ordinal } ?: -1

            val junction = DiaryStyleJunctionEntity.new {
                diary = diaryEntity
                this.style = styleEntity
                ordinal = maxOrdinal + 1
                enabled = style.enabled
            }

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = junction.enabled,
                styleFileUri = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun addDiaryStyleWithFile(userId: UUID, diaryLogin: String, name: String, styleFile: FileUploadData, enabled: Boolean): DiaryStyle {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val blogFile = storageService.store(userId, listOf(styleFile))[0]

            val styleEntity = DiaryStyleEntity.new {
                this.name = name
                this.styleFile = FileEntity.findById(blogFile.id) ?: throw InternalServerError()
            }

            // Create the junction entity to link the diary and style
            val maxOrdinal = diaryEntity.styleJunctions.maxOfOrNull { it.ordinal } ?: -1

            val junction = DiaryStyleJunctionEntity.new {
                diary = diaryEntity
                this.style = styleEntity
                ordinal = maxOrdinal + 1
                this.enabled = enabled
            }

            DiaryStyle(
                id = styleEntity.id.value,
                name = styleEntity.name,
                enabled = junction.enabled,
                styleFileUri = storageService.getFileURL(styleEntity.styleFile.toBlogFile()),
                previewPictureUri = styleEntity.previewPictureUri
            )
        }
    }

    override fun updateDiaryStyle(userId: UUID, diaryLogin: String, styleId: UUID, update: DiaryStyleUpdate): DiaryStyle {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val junction = DiaryStyleJunctionEntity.find {
                (DiaryStyleJunctions.style eq styleEntity.id) and (DiaryStyleJunctions.diary eq diaryEntity.id) 
            }.singleOrNull() ?: throw InvalidStyleException()

            val styleUploadData = FileUploadData("style.css", update.styleContent.byteInputStream())
            val blogFile = storageService.store(userId, listOf(styleUploadData))[0]

            val newStyleEntity = DiaryStyleEntity.new {
                name = update.name
                previewPictureUri = update.previewPictureUri
                styleFile = FileEntity.findById(blogFile.id)!!
            }

            junction.style = newStyleEntity
            junction.enabled = update.enabled

            DiaryStyle(
                id = newStyleEntity.id.value,
                name = newStyleEntity.name,
                enabled = junction.enabled,
                styleFileUri = storageService.getFileURL(newStyleEntity.styleFile.toBlogFile()),
                previewPictureUri = newStyleEntity.previewPictureUri,
            )
        }
    }

    override fun updateDiaryStyleWithFile(userId: UUID, diaryLogin: String, styleId: UUID, styleFile: FileUploadData): DiaryStyle {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            if (diaryEntity.owner.value != userId) throw WrongUserException()

            // Find the junction that links this style to the specified diary
            val junction = DiaryStyleJunctionEntity.find { 
                (DiaryStyleJunctions.style eq styleEntity.id) and (DiaryStyleJunctions.diary eq diaryEntity.id) 
            }.singleOrNull() ?: throw InvalidStyleException()

            val blogFile = storageService.store(userId, listOf(styleFile))[0]

            val newStyleEntity = DiaryStyleEntity.new {
                name = styleEntity.name
                previewPictureUri = styleEntity.previewPictureUri
                this.styleFile = FileEntity.findById(blogFile.id)!!
            }

            junction.style = newStyleEntity

            DiaryStyle(
                id = newStyleEntity.id.value,
                name = newStyleEntity.name,
                enabled = junction.enabled,
                styleFileUri = storageService.getFileURL(newStyleEntity.styleFile.toBlogFile()),
                previewPictureUri = newStyleEntity.previewPictureUri
            )
        }
    }

    override fun updateDiaryStylePreview(userId: UUID, diaryLogin: String, styleId: UUID, previewFile: FileUploadData): DiaryStyle {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()

            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val junction = DiaryStyleJunctionEntity.find {
                (DiaryStyleJunctions.style eq styleEntity.id) and (DiaryStyleJunctions.diary eq diaryEntity.id) 
            }.singleOrNull() ?: throw InvalidStyleException()

            val blogFile = storageService.store(userId, listOf(previewFile))[0]

            val newStyleEntity = DiaryStyleEntity.new {
                name = styleEntity.name
                styleFile = styleEntity.styleFile
                previewPictureUri = storageService.getFileURL(blogFile)
            }

            junction.style = newStyleEntity

            DiaryStyle(
                id = newStyleEntity.id.value,
                name = newStyleEntity.name,
                enabled = junction.enabled,
                styleFileUri = storageService.getFileURL(newStyleEntity.styleFile.toBlogFile()),
                previewPictureUri = newStyleEntity.previewPictureUri
            )
        }
    }

    override fun deleteDiaryStyle(userId: UUID, styleId: UUID): Boolean {
        return transaction {
            val styleEntity = DiaryStyleEntity.findById(styleId) ?: throw InvalidStyleException()

            // Find all junctions that link this style to diaries owned by the user
            val junctions = DiaryStyleJunctionEntity.find { DiaryStyleJunctions.style eq styleEntity.id }
                .filter { it.diary.owner.value == userId }

            if (junctions.isEmpty()) throw WrongUserException()

            // Delete all junctions
            junctions.forEach { it.delete() }

            // If no other diaries are using this style, delete the style entity too
            val remainingJunctions = DiaryStyleJunctionEntity.find { DiaryStyleJunctions.style eq styleEntity.id }.count()
            if (remainingJunctions == 0L) {
                styleEntity.delete()
            }

            true
        }
    }

    override fun reorderDiaryStyles(userId: UUID, diaryLogin: String, styleIds: List<UUID>): List<DiaryStyle> {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val junctions = diaryEntity.styleJunctions.toList()
            val junctionMap = junctions.associateBy { it.style.id.value }

            for (styleId in styleIds) {
                if (!junctionMap.containsKey(styleId)) throw InvalidStyleException()
            }

            if (styleIds.size != junctions.size) throw InvalidStyleException()

            for ((index, styleId) in styleIds.withIndex()) {
                junctionMap[styleId]?.ordinal = index
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
