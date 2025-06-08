package fi.lipp.blog.service

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.ReactionPackDto
import java.util.UUID

interface ReactionService {
    fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View
    fun deleteReaction(userId: UUID, name: String)
    fun getReactions(): List<ReactionDto.View>
    fun getBasicReactions(): List<ReactionPackDto>
    fun searchReactionsByName(namePattern: String): List<ReactionDto.View>
    fun getUserRecentReactions(userId: UUID, limit: Int = 50): List<ReactionDto.View>

    fun getReactions(names: List<String>): List<ReactionDto.View>

    // Post reactions
    fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String)
    fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String)

    // Comment reactions
    fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String)
    fun removeCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String)
    fun getCommentReactions(commentId: UUID): List<ReactionDto.ReactionInfo>
}
