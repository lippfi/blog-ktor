package fi.lipp.blog.domain

import fi.lipp.blog.repository.DiaryStyleJunctions
import fi.lipp.blog.repository.DiaryStyles
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class DiaryStyleEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryStyleEntity>(DiaryStyles)

    var name by DiaryStyles.name
    var styleFile by FileEntity referencedOn DiaryStyles.styleFile
    var previewPictureUri by DiaryStyles.previewPictureUri

    val diaryJunctions by DiaryStyleJunctionEntity referrersOn DiaryStyleJunctions.style
}
