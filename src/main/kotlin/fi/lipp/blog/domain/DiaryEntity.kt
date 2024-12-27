package fi.lipp.blog.domain

import fi.lipp.blog.repository.Diaries
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class DiaryEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryEntity>(Diaries)

    val name by Diaries.name
    var login: String by Diaries.login
    val creationTime by Diaries.creationTime

    val owner by Diaries.owner
    var style by Diaries.style
}