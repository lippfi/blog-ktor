package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object AnonymousUsers : UUIDTable() {
    val nickname = varchar("nickname", 50).index("idx_anonymous_nickname")
    val ipFingerprint = varchar("ip_fingerprint", 2048).index("idx_anonymous_ip_fingerprint")
    val email = varchar("email", 100).nullable().index("idx_anonymous_email")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
