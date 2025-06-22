package fi.lipp.blog.domain

import fi.lipp.blog.repository.DiaryStyleJunctions
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class DiaryStyleJunctionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryStyleJunctionEntity>(DiaryStyleJunctions)

    var diary by DiaryEntity referencedOn DiaryStyleJunctions.diary
    var style by DiaryStyleEntity referencedOn DiaryStyleJunctions.style
    var ordinal by DiaryStyleJunctions.ordinal
    var enabled by DiaryStyleJunctions.enabled
}