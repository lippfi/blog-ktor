package fi.lipp.blog.domain

import fi.lipp.blog.repository.ReactionSubsetReactions
import fi.lipp.blog.repository.ReactionSubsets
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class ReactionSubsetEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReactionSubsetEntity>(ReactionSubsets)

    var diary by ReactionSubsets.diary
    var name by ReactionSubsets.name
    var createdAt by ReactionSubsets.createdAt
}

class ReactionSubsetReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReactionSubsetReactionEntity>(ReactionSubsetReactions)

    var subset by ReactionSubsetReactions.subset
    var reaction by ReactionSubsetReactions.reaction
}
