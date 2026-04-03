package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object ReactionSubsets : UUIDTable() {
    val diary = reference("diary", Diaries, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }

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
