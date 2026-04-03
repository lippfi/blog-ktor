package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object InviteCodes : UUIDTable() {
    val creator = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val issuedAt = timestamp("issued_time").clientDefault { Clock.System.now() }
    val usedBy = reference("used_by", Users, onDelete = ReferenceOption.SET_NULL).nullable().default(null)
}
