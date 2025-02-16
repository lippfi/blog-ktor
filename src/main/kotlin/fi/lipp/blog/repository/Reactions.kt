package fi.lipp.blog.repository

import fi.lipp.blog.data.Language
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object Reactions : UUIDTable() {
    val name = varchar("name", 50)  // Technical name/identifier
    val icon = reference("icon", Files, onDelete = ReferenceOption.RESTRICT)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("reactions_name_unique", name)
    }
}

object ReactionLocalizations : UUIDTable() {
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)
    val language = enumerationByName("language", 20, Language::class)
    val localizedName = text("localized_name")

    init {
        uniqueIndex("reaction_localizations_unique", reaction, language)
    }
}

object PostReactions : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("post_reactions_unique", user, post, reaction)
    }
}

object AnonymousPostReactions : UUIDTable() {
    val ipFingerprint = varchar("ip_fingerprint", 2048)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)
    val timestamp = datetime("timestamp").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        uniqueIndex("anonymous_post_reactions_unique", ipFingerprint, post, reaction)
    }
}
