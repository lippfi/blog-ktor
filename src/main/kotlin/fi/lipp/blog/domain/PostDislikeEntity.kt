package fi.lipp.blog.domain

import fi.lipp.blog.repository.AnonymousPostDislikes
import fi.lipp.blog.repository.PostDislikes
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class PostDislikeEntity(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<PostDislikeEntity>(PostDislikes)
    
    val userId by PostDislikes.user
    val postId by PostDislikes.post
    val timestamp by PostDislikes.timestamp
}

class AnonymousPostDislikeEntity(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<AnonymousPostDislikeEntity>(AnonymousPostDislikes)
    
    val ipFingerprint by AnonymousPostDislikes.ipFingerprint
    val postId by AnonymousPostDislikes.post
    val timestamp by AnonymousPostDislikes.timestamp
}
