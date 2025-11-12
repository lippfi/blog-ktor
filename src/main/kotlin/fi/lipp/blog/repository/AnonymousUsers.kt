package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object AnonymousUsers : UUIDTable() {
    val nickname = varchar("nickname", 50).index("idx_anonymous_nickname")
    val ipFingerprint = varchar("ip_fingerprint", 2048).index("idx_anonymous_ip_fingerprint")
    val email = varchar("email", 100).nullable().index("idx_anonymous_email")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}
