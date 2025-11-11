package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object ExternalUsers : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE).nullable()
    val platformName = varchar("platform_name", 64)
    val externalUserId = varchar("external_user_id", 256)
    val nickname = varchar("nickname", 256)

    init { uniqueIndex("idx_external_user", externalUserId, platformName) }
}