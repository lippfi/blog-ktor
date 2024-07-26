package fi.lipp.blog.repository

import fi.lipp.blog.data.AccessGroupType
import org.jetbrains.exposed.dao.id.UUIDTable

object AccessGroups : UUIDTable() {
    val diary = reference("diary", Diaries).index("idx_access_group_owner").nullable()
    val name = varchar("name", 100)
    val type = enumerationByName<AccessGroupType>("type", 25).index("idx_access_group_type")
    val ordinal = integer("ordinal")
}