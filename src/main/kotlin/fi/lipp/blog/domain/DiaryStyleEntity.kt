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
    var description by DiaryStyles.description
    var styleFile by FileEntity referencedOn DiaryStyles.styleFile
    var author by UserEntity referencedOn DiaryStyles.author
}
