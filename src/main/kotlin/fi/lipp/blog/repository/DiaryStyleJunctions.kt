package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object DiaryStyleJunctions : UUIDTable() {
    val diary = reference("diary", Diaries, onDelete = ReferenceOption.CASCADE)
    val style = reference("style", DiaryStyles, onDelete = ReferenceOption.CASCADE)
    val ordinal = integer("ordinal")
    val enabled = bool("enabled")
}