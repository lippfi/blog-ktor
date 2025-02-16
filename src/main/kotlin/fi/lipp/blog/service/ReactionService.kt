package fi.lipp.blog.service

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import java.util.UUID

interface ReactionService {
    fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View
    fun deleteReaction(userId: UUID, name: String)
    fun getReactions(): List<ReactionDto.View>
    fun searchReactionsByName(namePattern: String): List<ReactionDto.View>
    fun getUserRecentReactions(userId: UUID, limit: Int = 50): List<ReactionDto.View>

    // Post reactions
    fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID)
    fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID)

    // Comment reactions
    fun addCommentReaction(viewer: Viewer, diaryLogin: String, uri: String, commentId: UUID, reactionId: UUID)
    fun removeCommentReaction(viewer: Viewer, diaryLogin: String, uri: String, commentId: UUID, reactionId: UUID)
    fun getCommentReactions(commentId: UUID): List<ReactionDto.ReactionInfo>
}
