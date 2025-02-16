package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.util.UUID

class ReactionServiceImpl(
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService
) : ReactionService {
    override fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View {
        // Validate reaction name
        ReactionDto.validateName(name)

        // Store the icon file first
        val storedFile = storageService.storeReaction(userId, icon)

        return transaction {
            // Get the file entity
            val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()

            val userEntity = UserEntity.findById(userId) ?: throw WrongUserException()
            val reactionEntity = ReactionEntity.new {
                this.name = name
                this.icon = iconFile
                this.creator = userEntity
            }
            toReactionView(reactionEntity)
        }
    }

    override fun deleteReaction(userId: UUID, name: String) {
        transaction {
            val reactionEntity = ReactionEntity.find { Reactions.name eq name }.firstOrNull() ?: throw ReactionNotFoundException()
            if (reactionEntity.creator.id.value != userId) {
                throw WrongUserException()
            }
            reactionEntity.delete()
        }
    }

    override fun getReactions(): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.all().map { toReactionView(it) }
        }
    }

    override fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value)) {
                throw WrongUserException()
            }
            when (viewer) {
                is Viewer.Registered -> {
                    val hasReaction = PostReactions.select { 
                        (PostReactions.user eq userId) and 
                        (PostReactions.post eq postEntity.id) and 
                        (PostReactions.reaction eq reactionId)
                    }.firstOrNull() != null
                    if (!hasReaction) {
                        PostReactions.insert {
                            it[user] = viewer.userId
                            it[post] = postEntity.id
                            it[reaction] = reactionId
                        }
                    }
                }
                is Viewer.Anonymous -> {
                    val hasReaction = AnonymousPostReactions.select { 
                        (AnonymousPostReactions.ipFingerprint eq viewer.ipFingerprint) and 
                        (AnonymousPostReactions.post eq postEntity.id) and 
                        (AnonymousPostReactions.reaction eq reactionId)
                    }.firstOrNull() != null
                    if (!hasReaction) {
                        AnonymousPostReactions.insert {
                            it[ipFingerprint] = viewer.ipFingerprint
                            it[post] = postEntity.id
                            it[reaction] = reactionId
                        }
                    }
                }
            }
        }
    }

    override fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionId: UUID) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value)) {
                throw WrongUserException()
            }
            when (viewer) {
                is Viewer.Registered -> {
                    val reaction = PostReactionEntity.find { 
                        (PostReactions.user eq userId) and 
                        (PostReactions.post eq postEntity.id) and 
                        (PostReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()
                }
                is Viewer.Anonymous -> {
                    val reaction = AnonymousPostReactionEntity.find { 
                        (AnonymousPostReactions.ipFingerprint eq viewer.ipFingerprint) and 
                        (AnonymousPostReactions.post eq postEntity.id) and 
                        (AnonymousPostReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()
                }
            }
        }
    }

    private fun toReactionView(reactionEntity: ReactionEntity): ReactionDto.View {
        return ReactionDto.View(
            id = reactionEntity.id.value,
            name = reactionEntity.name,
            iconUri = storageService.getFileURL(reactionEntity.icon.toBlogFile())
        )
    }

    private fun findDiaryByLogin(login: String): DiaryEntity {
        return DiaryEntity.find { Diaries.login eq login }.singleOrNull() ?: throw DiaryNotFoundException()
    }

    override fun searchReactionsByName(namePattern: String): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.find { Reactions.name like "%${namePattern}%" }
                .map { toReactionView(it) }
        }
    }

    override fun getUserRecentReactions(userId: UUID, limit: Int): List<ReactionDto.View> {
        return transaction {
            (PostReactions innerJoin Reactions)
                .slice(Reactions.columns)
                .select { PostReactions.user eq userId }
                .orderBy(PostReactions.timestamp to SortOrder.DESC)
                .limit(limit)
                .map { ReactionEntity.wrapRow(it) }
                .map { toReactionView(it) }
                .distinct()
        }
    }
}
