package fi.lipp.blog.domain

import fi.lipp.blog.repository.DiaryStyles
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class DiaryStyleEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryStyleEntity>(DiaryStyles)

    var name by DiaryStyles.name
    var ordinal by DiaryStyles.ordinal
    var enabled by DiaryStyles.enabled
    
    var diary by DiaryEntity referencedOn DiaryStyles.diary
    var styleFile by FileEntity referencedOn DiaryStyles.styleFile
    var previewPictureUri by DiaryStyles.previewPictureUri
}