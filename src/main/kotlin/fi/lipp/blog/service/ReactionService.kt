package fi.lipp.blog.service

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.ReactionPackDto
import java.util.UUID

interface ReactionService {
    fun createReaction(viewer: Viewer.Registered, name: String, packName: String, icon: FileUploadData): ReactionDto.View
    fun deleteReaction(viewer: Viewer.Registered, name: String)
    fun renameReaction(viewer: Viewer.Registered, oldName: String, newName: String)
    fun isReactionNameBusy(name: String): Boolean
    fun getReactions(): List<ReactionDto.View>
    fun getBasicReactions(): List<ReactionPackDto>
    fun searchReactionsByName(namePattern: String): List<ReactionDto.View>
    fun getUserRecentReactions(userId: UUID, limit: Int = 50): List<ReactionDto.View>

    fun getReactions(names: List<String>): List<ReactionDto.View>

    // Reaction pack management
    fun updateReactionPack(viewer: Viewer.Registered, packName: String, newName: String?, newIcon: FileUploadData?): ReactionPackDto

    // Post reactions
    fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String)
    fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String)

    // Comment reactions
    fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String)
    fun removeCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String)
    fun getCommentReactions(viewer: Viewer, commentId: UUID): List<ReactionDto.ReactionInfo>

    fun getMyPacks(viewer: Viewer.Registered): List<ReactionPackDto>

    fun search(text: String): List<ReactionDto.View>

    fun createReactionSubset(viewer: Viewer.Registered, diaryLogin: String, name: String, reactionNames: List<String>): UUID
    fun updateReactionSubset(viewer: Viewer.Registered, subsetId: UUID, name: String?, reactionNames: List<String>?)
    fun deleteReactionSubset(viewer: Viewer.Registered, subsetId: UUID)
}
