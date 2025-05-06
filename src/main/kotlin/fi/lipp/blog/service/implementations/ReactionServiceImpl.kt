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
) : ReactionService {
    // Basic reactions are stored in resources/img/reactions/basic
    private val basicReactions = mapOf(
        "heart.svg" to "heart",
        "fire.svg" to "fire"
    )

    // Smol reactions are stored in resources/img/reactions/smol
    private val smolReactions = mapOf(
        "sticker1.webp" to "smol-suicide",
        "sticker2.webp" to "smol-speech",
        "sticker3.webp" to "smol-shef",
        "sticker4.webp" to "smol-cereal",
        "sticker5.webp" to "smol-fyou",
        "sticker6.webp" to "smol-punch",
        "sticker7.webp" to "smol-peace",
        "sticker8.webp" to "smol-shock",
        "sticker9.webp" to "smol-sad",
        "sticker10.webp" to "smol-high",
        "sticker11.webp" to "smol-broken",
        "sticker12.webp" to "smol-crying",
        "sticker13.webp" to "smol-lick",
        "sticker14.webp" to "smol-milk",
        "sticker15.webp" to "smol-tears",
        "sticker16.webp" to "smol-alien",
        "sticker17.webp" to "smol-krakozyabra",
        "sticker18.webp" to "smol-hearts",
        "sticker19.webp" to "smol-crying2",
        "sticker20.webp" to "smol-annoyed",
        "sticker21.webp" to "smol-tilt",
        "sticker22.webp" to "smol-angry",
        "sticker23.webp" to "smol-suspicious",
        "sticker24.webp" to "smol-surprized",
        "sticker25.webp" to "smol-furious",
        "sticker26.webp" to "smol-nerd",
        "sticker27.webp" to "smol-nerd2",
        "sticker28.webp" to "smol-offended",
        "sticker29.webp" to "smol-love",
        "sticker30.webp" to "smol-happy",
        "sticker31.webp" to "smol-touched",
        "sticker32.webp" to "smol-ola",
        "sticker33.webp" to "smol-silly",
        "sticker34.webp" to "smol-insulted",
        "sticker35.webp" to "smol-shy",
        "sticker36.webp" to "smol-eating",
        "sticker37.webp" to "smol-potato",
        "sticker39.webp" to "smol-hisoka",
        "sticker40.webp" to "smol-runnynose",
        "sticker41.webp" to "smol-crying3",
        "sticker42.webp" to "smol-heart",
        "sticker43.webp" to "smol-love2",
        "sticker44.webp" to "smol-flowers",
        "sticker45.webp" to "smol-serious",
        "sticker46.webp" to "smol-wink",
        "sticker47.webp" to "smol-hehe",
        "sticker48.webp" to "smol-heheq",
        "sticker49.webp" to "smol-nothehe",
        "sticker50.webp" to "smol-talk",
        "sticker51.webp" to "smol-listening",
        "sticker52.webp" to "smol-night",
        "sticker53.webp" to "smol-clown",
        "sticker54.webp" to "smol-thumbsuppleased",
        "sticker55.webp" to "smol-thumbsupsad",
        "sticker56.webp" to "smol-thumbsup",
        "sticker57.webp" to "smol-horror",
        "sticker58.webp" to "smol-pleased",
        "sticker59.webp" to "smol-hearttears",
        "sticker60.webp" to "smol-silent",
        "sticker61.webp" to "smol-singing",
        "sticker62.webp" to "smol-handshake",
        "sticker63.webp" to "smol-smol",
//        "sticker64.webp" to "smol-hopeless",
//        "sticker65.webp" to "smol-desperate",
//        "sticker66.webp" to "smol-ecstatic",
//        "sticker68.webp" to "smol-furious",
//        "sticker69.webp" to "smol-delighted",
//        "sticker70.webp" to "smol-disgusted",
//        "sticker72.webp" to "smol-envious",
//        "sticker73.webp" to "smol-sympathetic",
//        "sticker74.webp" to "smol-empathetic",
//        "sticker75.webp" to "smol-compassionate",
//        "sticker76.webp" to "smol-resentful",
//        "sticker77.webp" to "smol-remorseful",
//        "sticker78.webp" to "smol-regretful",
//        "sticker79.webp" to "smol-appreciative",
//        "sticker80.webp" to "smol-admiring",
//        "sticker81.webp" to "smol-respectful",
//        "sticker82.webp" to "smol-trusting",
//        "sticker83.webp" to "smol-suspicious",
//        "sticker84.webp" to "smol-doubtful",
//        "sticker85.webp" to "smol-certain",
//        "sticker86.webp" to "smol-uncertain",
//        "sticker87.webp" to "smol-insecure",
        "sticker88.webp" to "smol-friends",
//        "sticker89.webp" to "smol-comfortable",
//        "sticker90.webp" to "smol-uncomfortable",
        "sticker91.webp" to "smol-angel",
        "sticker92.webp" to "smol-demon",
//        "sticker93.webp" to "smol-satisfied",
//        "sticker94.webp" to "smol-dissatisfied",
//        "sticker95.webp" to "smol-fulfilled",
//        "sticker96.webp" to "smol-empty",
//        "sticker97.webp" to "smol-complete",
//        "sticker98.webp" to "smol-incomplete",
//        "sticker99.webp" to "smol-whole",
//        "sticker100.webp" to "smol-broken",
//        "sticker101.webp" to "smol-connected",
//        "sticker102.webp" to "smol-disconnected",
//        "sticker103.webp" to "smol-engaged",
//        "sticker104.webp" to "smol-disengaged",
//        "sticker105.webp" to "smol-interested",
//        "sticker106.webp" to "smol-disinterested",
//        "sticker107.webp" to "smol-attentive",
//        "sticker108.webp" to "smol-distracted",
//        "sticker109.webp" to "smol-focused",
//        "sticker110.webp" to "smol-unfocused",
//        "sticker111.webp" to "smol-alert",
//        "sticker113.webp" to "smol-drowsy",
//        "sticker114.webp" to "smol-awake",
//        "sticker115.webp" to "smol-asleep",
//        "sticker116.webp" to "smol-dreaming"
    )

    private val cachedBasicReactions: List<ReactionPackDto> by lazy {
        val systemUserId = userService.getOrCreateSystemUser()

        val basicReactionViews = createReactionViews(systemUserId, basicReactions, "img/reactions/basic")
        val smolReactionViews = createReactionViews(systemUserId, smolReactions, "img/reactions/smol")

        listOf(
            createReactionPackDto(basicReactionViews),
            createReactionPackDto(smolReactionViews, "smol-alien")
        )
    }

    private fun createReactionViews(systemUserId: UUID, reactions: Map<String, String>, resourcePathPrefix: String): List<ReactionDto.View> {
        return transaction {
            reactions.map { (fileName, reactionName) ->
                if (!isReactionNameUsed(reactionName)) {
                    val resourcePath = "$resourcePathPrefix/$fileName"
                    val inputStream = this@ReactionServiceImpl::class.java.classLoader.getResourceAsStream(resourcePath)
                        ?: throw IllegalStateException("Resource not found: $resourcePath")

                    val fileUploadData = FileUploadData(
                        fullName = fileName,
                        inputStream = inputStream
                    )

                    val storedFile = storageService.storeReaction(systemUserId, fileUploadData)
                    val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()

                    ReactionEntity.new {
                        this.name = reactionName
                        this.icon = iconFile
                        this.creator = EntityID(systemUserId, Users)
                    }
                }

                val reactionEntity = ReactionEntity.find { Reactions.name eq reactionName }.first()
                toReactionView(reactionEntity)
            }
        }
    }

    private fun createReactionPackDto(reactionViews: List<ReactionDto.View>, icon: String? = null): ReactionPackDto {
        return ReactionPackDto(
            iconUri = icon?.let { reactionViews.first { it.name == icon }.iconUri } ?: reactionViews.firstOrNull()?.iconUri ?: "",
            reactions = reactionViews
        )
    }

    override fun getBasicReactions(): List<ReactionPackDto> = cachedBasicReactions

    override fun createReaction(userId: UUID, name: String, icon: FileUploadData): ReactionDto.View {
        ReactionDto.validateName(name)

        // TODO it is a race
        val isNameUsed = transaction { isReactionNameUsed(name) }
        if (isNameUsed) {
            throw ReactionNameIsTakenException()
        }

        val storedFile = storageService.storeReaction(userId, icon)

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
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value)) {
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

    override fun removeReaction(viewer: Viewer, diaryLogin: String, uri: String, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value)) {
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

    override fun addCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()

            // Check reaction permissions
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value)) {
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

    override fun removeCommentReaction(viewer: Viewer, commentId: UUID, reactionName: String) {
        val userId = (viewer as? Viewer.Registered)?.userId
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()

            // Check reaction permissions
            if (commentEntity.authorId.value != userId && !accessGroupService.inGroup(viewer, commentEntity.reactionGroupId.value)) {
                throw WrongUserException()
            }
            val reactionId = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()?.id ?: throw ReactionNotFoundException()

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
}
