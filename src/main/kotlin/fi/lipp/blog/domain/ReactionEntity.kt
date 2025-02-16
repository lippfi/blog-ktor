package fi.lipp.blog.domain

import fi.lipp.blog.data.Language
import fi.lipp.blog.repository.*
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class ReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReactionEntity>(Reactions)

    var name by Reactions.name
    var icon by FileEntity referencedOn Reactions.icon
}

class ReactionLocalizationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReactionLocalizationEntity>(ReactionLocalizations)

    var reaction by ReactionEntity referencedOn ReactionLocalizations.reaction
    var language by ReactionLocalizations.language
    var localizedName by ReactionLocalizations.localizedName
}

class PostReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostReactionEntity>(PostReactions)

    var user by UserEntity referencedOn PostReactions.user
    var post by PostEntity referencedOn PostReactions.post
    var reaction by ReactionEntity referencedOn PostReactions.reaction
}

class AnonymousPostReactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AnonymousPostReactionEntity>(AnonymousPostReactions)

    var ipFingerprint by AnonymousPostReactions.ipFingerprint
    var post by PostEntity referencedOn AnonymousPostReactions.post
    var reaction by ReactionEntity referencedOn AnonymousPostReactions.reaction
}
