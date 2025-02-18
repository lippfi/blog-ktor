package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object UserFollows : UUIDTable() {
    val follower = reference("follower_id", Users, onDelete = ReferenceOption.CASCADE)
    val following = reference("following_id", Users, onDelete = ReferenceOption.CASCADE)
    val followedAt = datetime("followed_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("idx_user_follows", follower, following)
    }
}