package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object PostDislikes : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}

object AnonymousPostDislikes : UUIDTable() {
    val ipFingerprint = varchar("ip_fingerprint", 2048)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
}
