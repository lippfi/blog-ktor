package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserFollows : UUIDTable() {
    val follower = reference("follower_id", Users, onDelete = ReferenceOption.CASCADE)
    val following = reference("following_id", Users, onDelete = ReferenceOption.CASCADE)
    val followedAt = timestamp("followed_at").clientDefault { Clock.System.now() }

    init {
        uniqueIndex("idx_user_follows", follower, following)
    }
}
