package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.transactions.transaction
import fi.lipp.blog.data.UserPermission
import java.io.FileNotFoundException
import java.util.*

class ReactionServiceImpl(
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService,
    private val notificationService: NotificationService,
    private val reactionDatabaseSeeder: ReactionDatabaseSeeder,
    private val commentWebSocketService: CommentWebSocketService
) : ReactionService {

    private val reactionLoader = ReactionLoader(storageService)

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

    override fun getMyPacks(viewer: Viewer.Registered): List<ReactionPackDto> {
        return transaction {
            val userPacks = ReactionPackEntity.find { ReactionPacks.creator eq viewer.userId }.toList()

            val includeBasic = UserPermission.WRITE_BASIC_REACTIONS in viewer.permissions
                    && userPacks.none { it.name == "basic" }

            val packs = if (includeBasic) {
                val basicPack = ReactionPackEntity.find { ReactionPacks.name eq "basic" }.firstOrNull()
                if (basicPack != null) userPacks + basicPack else userPacks
            } else {
                userPacks
            }

            packs.map { pack ->
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

    override fun createReaction(viewer: Viewer.Registered, name: String, packName: String, icon: FileUploadData): ReactionDto.View {
        ReactionDto.validateName(name)

        val userId = viewer.userId
        val storedFile = try {
            storageService.storeReaction(viewer, name, icon)
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

                // Check if user is allowed to add reactions to this pack
                val isPackCreator = pack.creator.id.value == userId
                val canWriteBasic = packName == "basic" && UserPermission.WRITE_BASIC_REACTIONS in viewer.permissions
                if (!isPackCreator && !canWriteBasic) {
                    throw WrongUserException()
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

    override fun deleteReaction(viewer: Viewer.Registered, name: String) {
        transaction {
            val reactionEntity = ReactionEntity.find { Reactions.name eq name }.firstOrNull() ?: throw ReactionNotFoundException()
            val isCreator = reactionEntity.creator.value == viewer.userId
            val canWriteBasic = reactionEntity.pack.name == "basic" && UserPermission.WRITE_BASIC_REACTIONS in viewer.permissions
            if (!isCreator && !canWriteBasic) {
                throw WrongUserException()
            }
            reactionEntity.delete()
        }
    }

    override fun renameReaction(viewer: Viewer.Registered, oldName: String, newName: String) {
        ReactionDto.validateName(newName)
        transaction {
            val reactionEntity = ReactionEntity.find { Reactions.name eq oldName }.firstOrNull() ?: throw ReactionNotFoundException()
            val isCreator = reactionEntity.creator.value == viewer.userId
            val canWriteBasic = reactionEntity.pack.name == "basic" && UserPermission.WRITE_BASIC_REACTIONS in viewer.permissions
            if (!isCreator && !canWriteBasic) {
                throw WrongUserException()
            }
            val nameExists = ReactionEntity.find { Reactions.name eq newName }.firstOrNull() != null
            if (nameExists) {
                throw ReactionNameIsTakenException()
            }
            reactionEntity.name = newName
        }
    }

    override fun isReactionNameBusy(name: String): Boolean {
        return transaction {
            ReactionEntity.find { Reactions.name eq name }.firstOrNull() != null
        }
    }

    override fun updateReactionPack(viewer: Viewer.Registered, packName: String, newName: String?, newIcon: FileUploadData?): ReactionPackDto {
        return transaction {
            val pack = ReactionPackEntity.find { ReactionPacks.name eq packName }.firstOrNull()
                ?: throw ReactionPackNotFoundException()
            val isCreator = pack.creator.id.value == viewer.userId
            val canWriteBasic = packName == "basic" && UserPermission.WRITE_BASIC_REACTIONS in viewer.permissions
            if (!isCreator && !canWriteBasic) {
                throw WrongUserException()
            }

            if (newName != null && newName != pack.name) {
                val nameExists = ReactionPackEntity.find { ReactionPacks.name eq newName }.firstOrNull() != null
                if (nameExists) {
                    throw ReactionNameIsTakenException()
                }
                pack.name = newName
            }

            if (newIcon != null) {
                val storedFile = storageService.storeReaction(
                    viewer,
                    "pack-icon-${pack.name}",
                    newIcon
                )
                val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()
                pack.icon = iconFile
            }

            ReactionPackDto(
                name = pack.name,
                iconUri = pack.icon?.let { storageService.getFileURL(it.toBlogFile()) }
                    ?: pack.reactions.minByOrNull { it.ordinal }?.let { storageService.getFileURL(it.icon.toBlogFile()) }
                    ?: "",
                reactions = pack.reactions.sortedBy { it.ordinal }.map { toReactionView(it) }
            )
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
                    throw WrongUserException() // todo throw better exception
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

    override fun createReactionSubset(viewer: Viewer.Registered, diaryLogin: String, name: String, reactionNames: List<String>): UUID {
        return transaction {
            val userDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.first()
            if (userDiary.owner.value != viewer.userId) {
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

    override fun updateReactionSubset(viewer: Viewer.Registered, subsetId: UUID, name: String?, reactionNames: List<String>?) {
        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId) ?: return@transaction
            val subsetDiary = DiaryEntity.findById(subset.diary)!!
            val diaryOwner = subsetDiary.owner
            if (diaryOwner.value != viewer.userId) throw WrongUserException()

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

    override fun deleteReactionSubset(viewer: Viewer.Registered, subsetId: UUID) {
        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId) ?: return@transaction
            val subsetDiary = DiaryEntity.findById(subset.diary)!!
            val diaryOwner = subsetDiary.owner
            if (diaryOwner.value != viewer.userId) throw WrongUserException()
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
            reactionLoader.loadCommentReactions(this, setOf(commentId), viewer)[commentId] ?: emptyList()
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
