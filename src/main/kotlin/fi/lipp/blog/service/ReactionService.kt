package fi.lipp.blog.service

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import java.util.UUID

interface ReactionService {
    fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View
    fun deleteReaction(userId: UUID, name: String)
    fun getReactions(): List<ReactionDto.View>

    fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID)
    fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID)
}
