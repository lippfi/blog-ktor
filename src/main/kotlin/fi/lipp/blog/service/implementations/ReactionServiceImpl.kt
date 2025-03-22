package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.ReactionPackDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.util.UUID

class ReactionServiceImpl(
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService,
    private val notificationService: NotificationService,
    private val config: ApplicationConfig
) : ReactionService {

    // Map of sticker file names to reaction names
    private val smolReactions = mapOf(
        "sticker1.webp" to "suicide",
        "sticker2.webp" to "speech",
        "sticker3.webp" to "shef",
        "sticker4.webp" to "cereal",
        "sticker5.webp" to "fyou",
        "sticker6.webp" to "punch",
        "sticker7.webp" to "peace",
        "sticker8.webp" to "shock",
        "sticker9.webp" to "sad",
        "sticker10.webp" to "high",
        "sticker11.webp" to "broken",
        "sticker12.webp" to "crying",
        "sticker13.webp" to "lick",
        "sticker14.webp" to "milk",
        "sticker15.webp" to "tears",
        "sticker16.webp" to "alien",
        "sticker17.webp" to "krakozyabra",
        "sticker18.webp" to "hearts",
        "sticker19.webp" to "crying2",
        "sticker20.webp" to "annoyed",
        "sticker21.webp" to "tilt",
        "sticker22.webp" to "angry",
        "sticker23.webp" to "suspicious",
        "sticker24.webp" to "surprized",
        "sticker25.webp" to "furious",
        "sticker26.webp" to "nerd",
        "sticker27.webp" to "nerd2",
        "sticker28.webp" to "offended",
        "sticker29.webp" to "love",
        "sticker30.webp" to "happy",
        "sticker31.webp" to "touched",
        "sticker32.webp" to "ola",
        "sticker33.webp" to "silly",
        "sticker34.webp" to "insulted",
        "sticker35.webp" to "shy",
        "sticker36.webp" to "eating",
        "sticker37.webp" to "potato",
        "sticker39.webp" to "hisoka",
        "sticker40.webp" to "runnynose",
        "sticker41.webp" to "crying3",
        "sticker42.webp" to "heart",
        "sticker43.webp" to "love2",
        "sticker44.webp" to "flowers",
        "sticker45.webp" to "serious",
        "sticker46.webp" to "wink",
        "sticker47.webp" to "hehe",
        "sticker48.webp" to "heheq",
        "sticker49.webp" to "nothehe",
        "sticker50.webp" to "talk",
        "sticker51.webp" to "listening",
//        "sticker52.webp" to "stressed",
//        "sticker53.webp" to "calm",
//        "sticker54.webp" to "energetic",
//        "sticker55.webp" to "lazy",
//        "sticker56.webp" to "motivated",
//        "sticker57.webp" to "overwhelmed",
//        "sticker58.webp" to "playful",
//        "sticker59.webp" to "serious",
//        "sticker60.webp" to "silly",
//        "sticker61.webp" to "grumpy",
//        "sticker62.webp" to "joyful",
//        "sticker63.webp" to "melancholic",
//        "sticker64.webp" to "hopeless",
//        "sticker65.webp" to "desperate",
//        "sticker66.webp" to "ecstatic",
//        "sticker68.webp" to "furious",
//        "sticker69.webp" to "delighted",
//        "sticker70.webp" to "disgusted",
//        "sticker72.webp" to "envious",
//        "sticker73.webp" to "sympathetic",
//        "sticker74.webp" to "empathetic",
//        "sticker75.webp" to "compassionate",
//        "sticker76.webp" to "resentful",
//        "sticker77.webp" to "remorseful",
//        "sticker78.webp" to "regretful",
//        "sticker79.webp" to "appreciative",
//        "sticker80.webp" to "admiring",
//        "sticker81.webp" to "respectful",
//        "sticker82.webp" to "trusting",
//        "sticker83.webp" to "suspicious",
//        "sticker84.webp" to "doubtful",
//        "sticker85.webp" to "certain",
//        "sticker86.webp" to "uncertain",
//        "sticker87.webp" to "insecure",
        "sticker88.webp" to "friends",
//        "sticker89.webp" to "comfortable",
//        "sticker90.webp" to "uncomfortable",
//        "sticker91.webp" to "pleased",
//        "sticker92.webp" to "displeased",
//        "sticker93.webp" to "satisfied",
//        "sticker94.webp" to "dissatisfied",
//        "sticker95.webp" to "fulfilled",
//        "sticker96.webp" to "empty",
//        "sticker97.webp" to "complete",
//        "sticker98.webp" to "incomplete",
//        "sticker99.webp" to "whole",
//        "sticker100.webp" to "broken",
//        "sticker101.webp" to "connected",
//        "sticker102.webp" to "disconnected",
//        "sticker103.webp" to "engaged",
//        "sticker104.webp" to "disengaged",
//        "sticker105.webp" to "interested",
//        "sticker106.webp" to "disinterested",
//        "sticker107.webp" to "attentive",
//        "sticker108.webp" to "distracted",
//        "sticker109.webp" to "focused",
//        "sticker110.webp" to "unfocused",
//        "sticker111.webp" to "alert",
//        "sticker113.webp" to "drowsy",
//        "sticker114.webp" to "awake",
//        "sticker115.webp" to "asleep",
//        "sticker116.webp" to "dreaming"
    )


    override fun getBasicReactions(): List<ReactionPackDto> {
        TODO() // return basic pack + small pack
    }
    override fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View {
        // Validate reaction name
        ReactionDto.validateName(name)

        // TODO it is a race
        val isNameUsed = transaction { isReactionNameUsed(name) }
        if (isNameUsed) {
            throw ReactionNameIsTakenException()
        }

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

    private fun Transaction.isReactionNameUsed(name: String): Boolean {
        return ReactionEntity.find { Reactions.name eq name }.firstOrNull() != null
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

                        val postAuthor = UserEntity.findById(postEntity.authorId.value)!!
                        if (postAuthor.id.value != viewer.userId && postAuthor.notifyAboutPostReactions) {
                            notificationService.notifyAboutPostReaction(postEntity.id.value)
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

                        val postAuthor = UserEntity.findById(postEntity.authorId.value)!!
                        if (postAuthor.notifyAboutPostReactions) {
                            notificationService.notifyAboutPostReaction(postEntity.id.value)
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

    override fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionId: UUID) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()

            // Check reaction permissions
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value)) {
                throw WrongUserException()
            }

            when (viewer) {
                is Viewer.Registered -> {
                    val hasReaction = CommentReactions.select { 
                        (CommentReactions.user eq userId) and 
                        (CommentReactions.comment eq commentId) and 
                        (CommentReactions.reaction eq reactionId)
                    }.firstOrNull() != null
                    if (!hasReaction) {
                        CommentReactions.insert {
                            it[user] = viewer.userId
                            it[comment] = commentId
                            it[reaction] = reactionId
                        }

                        // Create notification for comment author
                        if (viewer.userId != commentEntity.authorId.value) {
                            notificationService.notifyAboutCommentReaction(commentEntity.authorId.value)
                        }
                    }
                }
                is Viewer.Anonymous -> {
                    val hasReaction = AnonymousCommentReactions.select { 
                        (AnonymousCommentReactions.ipFingerprint eq viewer.ipFingerprint) and 
                        (AnonymousCommentReactions.comment eq commentId) and 
                        (AnonymousCommentReactions.reaction eq reactionId)
                    }.firstOrNull() != null
                    if (!hasReaction) {
                        AnonymousCommentReactions.insert {
                            it[ipFingerprint] = viewer.ipFingerprint
                            it[comment] = commentId
                            it[reaction] = reactionId
                        }
                    }
                }
            }
        }
    }

    override fun removeCommentReaction(viewer: Viewer, commentId: UUID, reactionId: UUID) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()

            // Check reaction permissions
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value)) {
                throw WrongUserException()
            }

            when (viewer) {
                is Viewer.Registered -> {
                    val reaction = CommentReactionEntity.find { 
                        (CommentReactions.user eq userId) and 
                        (CommentReactions.comment eq commentId) and 
                        (CommentReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()
                }
                is Viewer.Anonymous -> {
                    val reaction = AnonymousCommentReactionEntity.find { 
                        (AnonymousCommentReactions.ipFingerprint eq viewer.ipFingerprint) and 
                        (AnonymousCommentReactions.comment eq commentId) and 
                        (AnonymousCommentReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()
                }
            }
        }
    }

    override fun getCommentReactions(commentId: UUID): List<ReactionDto.ReactionInfo> {
        return transaction {
            // Get reactions with their files
            val reactionData = (CommentReactions innerJoin Reactions innerJoin Files)
                .slice(Reactions.id, Reactions.name, Files.id)
                .select { CommentReactions.comment eq commentId }
                .map { row ->
                    Triple(
                        row[Reactions.id].value,
                        row[Reactions.name],
                        row[Files.id].value
                    )
                }.distinct()

            // Get user logins for each reaction
            val reactionUsers = mutableMapOf<UUID, MutableList<String>>()
            reactionData.forEach { (reactionId, _, _) ->
                val userLogins = (CommentReactions innerJoin Users innerJoin Diaries)
                    .slice(Diaries.login)
                    .select { (CommentReactions.comment eq commentId) and (CommentReactions.reaction eq reactionId) }
                    .map { it[Diaries.login] }
                    .distinct()
                reactionUsers[reactionId] = userLogins.toMutableList()
            }

            // Get anonymous reactions count
            val anonymousCounts = mutableMapOf<UUID, Int>()
            reactionData.forEach { (reactionId, _, _) ->
                val count = AnonymousCommentReactions
                    .select { (AnonymousCommentReactions.comment eq commentId) and (AnonymousCommentReactions.reaction eq reactionId) }
                    .count()
                anonymousCounts[reactionId] = count.toInt()
            }

            // Create ReactionInfo objects
            reactionData.map { (reactionId, name, fileId) ->
                val userLogins = reactionUsers[reactionId] ?: emptyList()
                val anonymousCount = anonymousCounts[reactionId] ?: 0

                ReactionDto.ReactionInfo(
                    id = reactionId,
                    name = name,
                    iconUri = storageService.getFileURL(FileEntity.findById(fileId)!!.toBlogFile()),
                    count = userLogins.size + anonymousCount,
                    userLogins = userLogins,
                    anonymousCount = anonymousCount
                )
            }
        }
    }
}
