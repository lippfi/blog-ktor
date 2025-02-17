package fi.lipp.blog.domain

import fi.lipp.blog.repository.FriendRequests
import fi.lipp.blog.repository.Friends
import fi.lipp.blog.repository.FriendLabels
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class FriendRequestEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FriendRequestEntity>(FriendRequests)

    var fromUser: EntityID<UUID> by FriendRequests.fromUser
    var toUser: EntityID<UUID> by FriendRequests.toUser
    var message: String by FriendRequests.message
    var label: String? by FriendRequests.label
    var createdAt: LocalDateTime by FriendRequests.createdAt
}

class FriendshipEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FriendshipEntity>(Friends)

    var user1: EntityID<UUID> by Friends.user1
    var user2: EntityID<UUID> by Friends.user2
    var createdAt: LocalDateTime by Friends.createdAt
}

class FriendLabelEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FriendLabelEntity>(FriendLabels)

    var user: EntityID<UUID> by FriendLabels.user
    var friend: EntityID<UUID> by FriendLabels.friend
    var label: String? by FriendLabels.label
    var createdAt: LocalDateTime by FriendLabels.createdAt
}
