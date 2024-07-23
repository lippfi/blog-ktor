package fi.lipp.blog.domain

import fi.lipp.blog.repository.Diaries
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class DiaryEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DiaryEntity>(Diaries)

    val name by Diaries.name
    val creationTime by Diaries.creationTime

    val owner by Diaries.owner
    var style by Diaries.style
}