package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object Tags : LongIdTable() {
    val diary = reference("diary", Diaries, onDelete = ReferenceOption.CASCADE).index("idx_tag_diary")
    val name = varchar("name", length = 100).index("idx_tag_name")
}