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
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.util.*

class ReactionServiceImpl(
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService,
    private val notificationService: NotificationService,
    private val userService: UserService,
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

        val basicReactionViews = getReactionsByPrefix("heart", "fire")
        val smolReactionViews = getReactionsByPrefix("smol-")

        listOf(
            createReactionPackDto(basicReactionViews),
            createReactionPackDto(smolReactionViews, "smol-alien")
        )
    }

    private fun getReactionsByPrefix(vararg prefixes: String): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.all()
                .filter { entity -> 
                    prefixes.any { prefix -> 
                        entity.name.startsWith(prefix) || entity.name == prefix 
                    }
                }
                .map { toReactionView(it) }
        }
    }

    private fun createReactionPackDto(reactionViews: List<ReactionDto.View>, icon: String? = null): ReactionPackDto {
        return ReactionPackDto(
            iconUri = icon?.let { reactionViews.firstOrNull { it.name == icon }?.iconUri } ?: reactionViews.firstOrNull()?.iconUri ?: "",
            reactions = reactionViews
        )
    }

    override fun getBasicReactions(): List<ReactionPackDto> {
        return cachedBasicReactions
    }

    override fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View {
        ReactionDto.validateName(name)

        // TODO it is a race
        val isNameUsed = transaction { isReactionNameUsed(name) }
        if (isNameUsed) {
            throw ReactionNameIsTakenException()
        }

        val extension = icon.extension

        val storedFile = storageService.storeReaction(userId, name + extension, icon)

        return transaction {
            val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()

            val reactionEntity = ReactionEntity.new {
                this.name = name
                this.icon = iconFile
                this.creator = EntityID(userId, Users)
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
            if (reactionEntity.creator.value != userId) {
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

    override fun addReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val diaryOwnerId = diaryEntity.owner.value
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()
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
                        if (postAuthor.id.value != viewer.userId && isNotificationEnabled(postAuthor.id.value) { entity -> entity.notifyAboutPostReactions }) {
                            notificationService.notifyAboutPostReaction(viewer.userId, postEntity.id.value)
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

    override fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val diaryOwnerId = diaryEntity.owner.value
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value, diaryOwnerId)) {
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
            ReactionEntity.find { Reactions.name inList names }.map { toReactionView(it) }
        }
    }

    override fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postEntity = PostEntity.findById(commentEntity.postId)!!
            val diaryOwnerId = DiaryEntity.findById(postEntity.diaryId)!!.owner.value

            // Check reaction permissions
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value, diaryOwnerId)) {
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
                        if (viewer.userId != commentEntity.authorId.value) {
                            notificationService.notifyAboutCommentReaction(commentEntity.authorId.value)
                        }

                        // Send WebSocket notification about reaction added
                        val reactionInfo = getCommentReactions(commentId).find { it.id == reactionId.value }
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
                        val reactionInfo = getCommentReactions(commentId).find { it.id == reactionId.value }
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
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value, diaryOwnerId)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()

            // Get reaction info before deleting it
            val reactionInfo = getCommentReactions(commentId).find { it.id == reactionId.value }

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

            // Get user logins and nicknames for each reaction
            val reactionUsers = mutableMapOf<UUID, MutableList<ReactionDto.UserInfo>>()
            reactionData.forEach { (reactionId, _, _) ->
                val userInfos = (CommentReactions innerJoin Users innerJoin Diaries)
                    .slice(Diaries.login, Users.nickname)
                    .select { (CommentReactions.comment eq commentId) and (CommentReactions.reaction eq reactionId) }
                    .map { ReactionDto.UserInfo(login = it[Diaries.login], nickname = it[Users.nickname]) }
                    .distinct()
                reactionUsers[reactionId] = userInfos.toMutableList()
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
                    users = userLogins,
                    anonymousCount = anonymousCount
                )
            }
        }
    }

    override fun search(text: String): List<ReactionDto.View> {
        return transaction {
            ReactionEntity.find { Reactions.name like "%${text}%" }
                .orderBy(Reactions.name to SortOrder.ASC)
                .limit(120)
                .map { toReactionView(it) }
        }
    }
}
