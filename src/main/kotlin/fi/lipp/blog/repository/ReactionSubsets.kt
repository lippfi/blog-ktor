package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

object ReactionSubsets : UUIDTable() {
    val diary = reference("diary", Diaries, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }

    init {
        index("reaction_subsets_diary_idx", false, diary)
    }
}

object ReactionSubsetReactions : UUIDTable() {
    val subset = reference("subset", ReactionSubsets, onDelete = ReferenceOption.CASCADE)
    val reaction = reference("reaction", Reactions, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex("subset_reaction_unique", subset, reaction)
    }
}
