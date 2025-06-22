package fi.lipp.blog.domain

import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.DiaryStyleJunctions
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class DiaryEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DiaryEntity>(Diaries)

    var name by Diaries.name
    var subtitle by Diaries.subtitle

    var login: String by Diaries.login
    val creationTime by Diaries.creationTime

    val owner by Diaries.owner

    // Style is now managed through DiaryStyleJunctionEntity
    val styleJunctions by DiaryStyleJunctionEntity referrersOn DiaryStyleJunctions.diary

    var defaultReadGroup by Diaries.defaultReadGroup
    var defaultCommentGroup by Diaries.defaultCommentGroup
    var defaultReactGroup by Diaries.defaultReactGroup
}
