package fi.lipp.blog.domain

import fi.lipp.blog.repository.UserFollows
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserFollowEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserFollowEntity>(UserFollows)

    var follower by UserEntity referencedOn UserFollows.follower
    var following by UserEntity referencedOn UserFollows.following
    var followedAt: LocalDateTime by UserFollows.followedAt
}