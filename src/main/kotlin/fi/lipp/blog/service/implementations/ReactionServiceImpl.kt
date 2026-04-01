package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.ReactionPackDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.util.*

class ReactionServiceImpl(
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService,
    private val notificationService: NotificationService,
    private val reactionDatabaseSeeder: ReactionDatabaseSeeder,
    private val commentWebSocketService: CommentWebSocketService
) : ReactionService {

    /**
     * Helper method to get notification settings entity for a user.
     * Returns the entity or null if not found.
     */
    private fun getNotificationSettingsEntity(userId: UUID): NotificationSettingsEntity? {
        return NotificationSettingsEntity.find { 
            fi.lipp.blog.repository.NotificationSettings.user eq userId 
        }.firstOrNull()
    }

    /**
     * Helper method to check if a specific notification setting is enabled for a user.
     * Returns true if the setting is enabled or if no settings are found (default behavior).
     */
    private fun isNotificationEnabled(userId: UUID, setting: (NotificationSettingsEntity) -> Boolean): Boolean {
        val entity = getNotificationSettingsEntity(userId)
        return entity?.let(setting) ?: true
    }

    private val cachedBasicReactions: List<ReactionPackDto> by lazy {
        // Ensure reactions are seeded
        reactionDatabaseSeeder.seed()

        transaction {
            ReactionPackEntity.all().map { pack ->
                ReactionPackDto(
                    name = pack.name,
                    iconUri = pack.icon?.let { storageService.getFileURL(it.toBlogFile()) }
                        ?: pack.reactions.minByOrNull { it.ordinal }?.let { storageService.getFileURL(it.icon.toBlogFile()) } 
                        ?: "",
                    reactions = pack.reactions.sortedBy { it.ordinal }.map { toReactionView(it) }
                )
            }
        }
    }

    override fun getBasicReactions(): List<ReactionPackDto> {
        return cachedBasicReactions
    }

    override fun createReaction(userId: UUID, name: String, packName: String, icon: FileUploadData): ReactionDto.View {
        ReactionDto.validateName(name)

        val storedFile = try {
            storageService.storeReaction(userId, name, icon)
        } catch (_: ReactionAlreadyExistsException) {
            throw ReactionNameIsTakenException()
        }

        return try {
            transaction {
                val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()
                val pack = ReactionPackEntity.find { ReactionPacks.name eq packName }.firstOrNull() 
                    ?: ReactionPackEntity.new { 
                        this.name = packName
                        this.creator = UserEntity.findById(userId)!!
                    }

                val reactionEntity = ReactionEntity.new {
                    this.name = name
                    this.icon = iconFile
                    this.pack = pack
                    this.creator = EntityID(userId, Users)

                    val maxOrdinal = Reactions.slice(Reactions.ordinal.max())
                        .select { Reactions.pack eq pack.id }
                        .map { it[Reactions.ordinal.max()] }
                        .firstOrNull() ?: -1
                    this.ordinal = maxOrdinal + 1
                }
                toReactionView(reactionEntity)
            }
        } catch (e: Exception) {
            transaction {
                Files.deleteWhere { Files.id eq storedFile.id }
            }
            throw e
        }
    }

    override fun deleteReaction(userId: UUID, name: String) {
        transaction {
            val reactionEntity = ReactionEntity.find { Reactions.name eq name }.firstOrNull() ?: throw ReactionNotFoundException()
            if (reactionEntity.creator.value != userId) {
                throw WrongUserException()
            }
            reactionEntity.delete()
        }
    }

    override fun getReactions(): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.all().sortedBy { it.ordinal }.map { toReactionView(it) }
        }
    }

    override fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val diaryOwnerId = diaryEntity.owner.value
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()

            val subsetId = postEntity.reactionSubsetId?.value
            if (subsetId != null) {
                val isAllowed = ReactionSubsetReactions.select {
                    (ReactionSubsetReactions.subset eq EntityID(subsetId, ReactionSubsets)) and
                    (ReactionSubsetReactions.reaction eq reactionId)
                }.any()
                if (!isAllowed) {
                    throw WrongUserException()
                }
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

                        val postAuthorId = postEntity.authorId
                        if (postAuthorId != null) {
                            val postAuthor = UserEntity.findById(postAuthorId)!!
                            if (postAuthor.id.value != viewer.userId && isNotificationEnabled(postAuthor.id.value) { entity -> entity.notifyAboutPostReactions }) {
                                notificationService.notifyAboutPostReaction(viewer.userId, postEntity.id.value)
                            }
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

                        // TODO no sender for notification
//                        val postAuthor = UserEntity.findById(postEntity.authorId.value)!!
//                        if (isNotificationEnabled(postAuthor.id.value) { entity -> entity.notifyAboutPostReactions }) {
//                            notificationService.notifyAboutPostReaction(postEntity.id.value)
//                        }
                    }
                }
            }
        }
    }

    override fun createReactionSubset(userId: UUID, diaryLogin: String, name: String, reactionNames: List<String>): UUID {
        return transaction {
            val userDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.first()
            if (userDiary.owner.value != userId) {
                throw WrongUserException()
            }

            val subset = ReactionSubsetEntity.new {
                this.diary = userDiary.id
                this.name = name
            }
            val subsetId = subset.id.value

            if (reactionNames.isNotEmpty()) {
                val reactions = ReactionEntity.find { Reactions.name inList reactionNames }.toList()
                // Ensure all provided names exist
                if (reactions.size != reactionNames.toSet().size) {
                    throw ReactionNotFoundException()
                }
                reactions.forEach { rx ->
                    ReactionSubsetReactionEntity.new {
                        this.subset = EntityID(subsetId, ReactionSubsets)
                        this.reaction = rx.id
                    }
                }
            }

            subsetId
        }
    }

    override fun updateReactionSubset(userId: UUID, subsetId: UUID, name: String?, reactionNames: List<String>?) {
        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId) ?: return@transaction
            val subsetDiary = DiaryEntity.findById(subset.diary)!!
            val diaryOwner = subsetDiary.owner
            if (diaryOwner.value != userId) throw WrongUserException()

            if (name != null) {
                subset.name = name
            }

            if (reactionNames != null) {
                ReactionSubsetReactions.deleteWhere { ReactionSubsetReactions.subset eq EntityID(subsetId, ReactionSubsets) }

                if (reactionNames.isNotEmpty()) {
                    val reactions = ReactionEntity.find { Reactions.name inList reactionNames }.toList()
                    if (reactions.size != reactionNames.toSet().size) {
                        throw ReactionNotFoundException()
                    }
                    for (reaction in reactions) {
                        ReactionSubsetReactions.insert {
                            it[ReactionSubsetReactions.subset] = subset.id
                            it[ReactionSubsetReactions.reaction] = reaction.id
                        }
                    }
                }
            }

        }
    }

    override fun deleteReactionSubset(userId: UUID, subsetId: UUID) {
        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId) ?: return@transaction
            val subsetDiary = DiaryEntity.findById(subset.diary)!!
            val diaryOwner = subsetDiary.owner
            if (diaryOwner.value != userId) throw WrongUserException()
            ReactionSubsets.deleteWhere { ReactionSubsets.id eq subsetId }
        }
    }

    override fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val diaryOwnerId = diaryEntity.owner.value
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()
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
            iconUri = storageService.getFileURL(reactionEntity.icon.toBlogFile()),
        )
    }

    private fun findDiaryByLogin(login: String): DiaryEntity {
        return DiaryEntity.find { Diaries.login eq login }.singleOrNull() ?: throw DiaryNotFoundException()
    }

    override fun searchReactionsByName(namePattern: String): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.find { Reactions.name like "%${namePattern}%" }
                .orderBy(Reactions.pack to SortOrder.ASC, Reactions.ordinal to SortOrder.ASC)
                .map { toReactionView(it) }
        }
    }

    override fun getUserRecentReactions(userId: UUID, limit: Int): List<ReactionDto.View> {
        return transaction {
            val postReactionsWithTimestamp = (PostReactions innerJoin Reactions)
                .slice(Reactions.columns + PostReactions.timestamp)
                .select { PostReactions.user eq userId }
                .orderBy(PostReactions.timestamp to SortOrder.DESC)
                .limit(limit)
                .map { 
                    val reaction = ReactionEntity.wrapRow(it)
                    val timestamp = it[PostReactions.timestamp]
                    Pair(reaction, timestamp)
                }

            val commentReactionsWithTimestamp = (CommentReactions innerJoin Reactions)
                .slice(Reactions.columns + CommentReactions.timestamp)
                .select { CommentReactions.user eq userId }
                .orderBy(CommentReactions.timestamp to SortOrder.DESC)
                .limit(limit)
                .map { 
                    val reaction = ReactionEntity.wrapRow(it)
                    val timestamp = it[CommentReactions.timestamp]
                    Pair(reaction, timestamp)
                }

            (postReactionsWithTimestamp + commentReactionsWithTimestamp)
                .sortedByDescending { it.second }
                .distinctBy { it.first.name }
                .take(limit)
                .map { toReactionView(it.first) }
        }
    }

    override fun getReactions(names: List<String>): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.find { Reactions.name inList names }
                .orderBy(Reactions.pack to SortOrder.ASC, Reactions.ordinal to SortOrder.ASC)
                .map { toReactionView(it) }
        }
    }

    override fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postEntity = PostEntity.findById(commentEntity.postId)!!
            val diaryOwnerId = DiaryEntity.findById(postEntity.diaryId)!!.owner.value

            // Check reaction permissions
            if (commentEntity.authorId != userId && !accessGroupService.inGroup(viewer, postEntity.commentReactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()

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
                        val commentAuthor = commentEntity.authorId
                        if (commentAuthor != null) {
                            if (viewer.userId != commentEntity.authorId) {
                                notificationService.notifyAboutCommentReaction(commentAuthor)
                            }
                        }

                        // Send WebSocket notification about reaction added
                        val reactionInfo = getCommentReactions(viewer, commentId).find { it.id == reactionId.value }
                        if (reactionInfo != null) {
                            commentWebSocketService.notifyReactionAdded(commentId, reactionInfo, postEntity.id.value)
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

                        // Send WebSocket notification about reaction added
                        val reactionInfo = getCommentReactions(viewer, commentId).find { it.id == reactionId.value }
                        if (reactionInfo != null) {
                            commentWebSocketService.notifyReactionAdded(commentId, reactionInfo, postEntity.id.value)
                        }
                    }
                }
            }
        }
    }

    override fun removeCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postEntity = PostEntity.findById(commentEntity.postId)!!
            val diaryOwnerId = DiaryEntity.findById(postEntity.diaryId)!!.owner.value

            // Check reaction permissions
            if (commentEntity.authorId != userId && !accessGroupService.inGroup(viewer, postEntity.commentReactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()

            // Get reaction info before deleting it
            val reactionInfo = getCommentReactions(viewer, commentId).find { it.id == reactionId.value }

            when (viewer) {
                is Viewer.Registered -> {
                    val reaction = CommentReactionEntity.find { 
                        (CommentReactions.user eq userId) and 
                        (CommentReactions.comment eq commentId) and 
                        (CommentReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()

                    // Send WebSocket notification about reaction removed
                    if (reactionInfo != null) {
                        commentWebSocketService.notifyReactionRemoved(commentId, reactionInfo, postEntity.id.value)
                    }
                }
                is Viewer.Anonymous -> {
                    val reaction = AnonymousCommentReactionEntity.find { 
                        (AnonymousCommentReactions.ipFingerprint eq viewer.ipFingerprint) and 
                        (AnonymousCommentReactions.comment eq commentId) and 
                        (AnonymousCommentReactions.reaction eq reactionId)
                    }.firstOrNull() ?: return@transaction
                    reaction.delete()

                    // Send WebSocket notification about reaction removed
                    if (reactionInfo != null) {
                        commentWebSocketService.notifyReactionRemoved(commentId, reactionInfo, postEntity.id.value)
                    }
                }
            }
        }
    }

    override fun getCommentReactions(viewer: Viewer, commentId: UUID): List<ReactionDto.ReactionInfo> {
        return transaction {
            val userId = (viewer as? Viewer.Registered)?.userId

            val ignoreConditions = if (userId != null) {
                val ignoredUsersSubquery = IgnoreList
                    .slice(IgnoreList.ignoredUser)
                    .select { IgnoreList.user eq userId }

                val usersWhoIgnoredMeSubquery = IgnoreList
                    .slice(IgnoreList.user)
                    .select { IgnoreList.ignoredUser eq userId }

                listOf(
                    CommentReactions.user notInSubQuery ignoredUsersSubquery,
                    CommentReactions.user notInSubQuery usersWhoIgnoredMeSubquery
                )
            } else {
                emptyList()
            }

            val baseConditions: List<Op<Boolean>> = listOf(CommentReactions.comment eq commentId)
            val allConditions = baseConditions + ignoreConditions

            // Get reactions with their files and users in one query
            val reactionData = (CommentReactions innerJoin Reactions innerJoin Files innerJoin Users innerJoin Diaries)
                .slice(
                    CommentReactions.reaction,
                    Reactions.name,
                    Files.id,
                    Diaries.login,
                    Users.nickname
                )
                .select { allConditions.reduce { acc, condition -> acc and condition } }
                .toList()

            if (reactionData.isEmpty()) return@transaction emptyList()

            val fileIds = reactionData.map { it[Files.id].value }.toSet()
            val fileUrlMap = fileIds.associateWith { fileId ->
                storageService.getFileURL(FileEntity.findById(fileId)!!.toBlogFile())
            }

            val userReactionMap = reactionData.groupBy(
                keySelector = { it[CommentReactions.reaction].value },
                valueTransform = { ReactionDto.UserInfo(it[Diaries.login], it[Users.nickname]) }
            )

            val reactionTypes = reactionData.map { row ->
                Triple(
                    row[CommentReactions.reaction].value,
                    row[Reactions.name],
                    row[Files.id].value
                )
            }.distinct()

            val reactionIds = reactionTypes.map { it.first }.toSet()

            val anonymousCounts = AnonymousCommentReactions
                .slice(AnonymousCommentReactions.reaction, AnonymousCommentReactions.reaction.count())
                .select {
                    (AnonymousCommentReactions.comment eq commentId) and
                            (AnonymousCommentReactions.reaction inList reactionIds)
                }
                .groupBy(AnonymousCommentReactions.reaction)
                .associate { row ->
                    row[AnonymousCommentReactions.reaction].value to row[AnonymousCommentReactions.reaction.count()].toInt()
                }

            reactionTypes.map { (reactionId, name, fileId) ->
                val users = userReactionMap[reactionId] ?: emptyList()
                val anonymousCount = anonymousCounts[reactionId] ?: 0

                ReactionDto.ReactionInfo(
                    id = reactionId,
                    name = name,
                    iconUri = fileUrlMap[fileId] ?: "",
                    count = users.size + anonymousCount,
                    users = users,
                    anonymousCount = anonymousCount
                )
            }
        }
    }

    override fun search(text: String): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.find { Reactions.name like "%${text}%" }
                .orderBy(Reactions.pack to SortOrder.ASC, Reactions.ordinal to SortOrder.ASC)
                .limit(120)
                .map { toReactionView(it) }
        }
    }
}
