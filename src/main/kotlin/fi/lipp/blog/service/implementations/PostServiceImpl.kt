package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.DiaryPage
import fi.lipp.blog.model.DiaryView
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.PostPage
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.*
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random

class PostServiceImpl(
    private val accessGroupService: AccessGroupService,
    private val storageService: StorageService,
    private val reactionService: ReactionService,
    private val notificationService: NotificationService,
    private val commentWebSocketService: CommentWebSocketService
) : PostService {
    override fun getPostForEdit(userId: UUID, postId: UUID): PostDto.Update {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (postEntity.authorId != userId) throw WrongUserException()
            return@transaction toPostUpdateData(postEntity)
        }
    }

    override fun getPreface(viewer: Viewer, diaryLogin: String): PostDto.View? {
        return transaction {
            val baseJoin = getBasicPostJoin()
            val q = baseJoin
                .slice(postBaseSlice)
                .select {
                    (postDiary[Diaries.login] eq diaryLogin) and
                            (Posts.isPreface eq true) and
                            (Posts.isArchived eq false)
                }
                .limit(1)

            val row = q.firstOrNull() ?: return@transaction null

            val diaryOwnerId = row[postDiary[Diaries.owner]].value
            val canRead = accessGroupService.inGroup(viewer, row[Posts.readGroup].value, diaryOwnerId) ||
                    ((viewer as? Viewer.Registered)?.userId?.let { uid ->
                        val isLocalAuthor = row[Posts.authorType] == PostAuthorType.LOCAL && row[localPostAuthor[Users.id]].value == uid
                        val isLinkedExternal = row[Posts.authorType] == PostAuthorType.EXTERNAL && row[externalPostAuthor[ExternalUsers.user]]?.value == uid
                        isLocalAuthor || isLinkedExternal
                    } ?: false)

            if (!canRead) return@transaction null

            val postId = row[Posts.id].value
            val rowsByPostId = mapOf(postId to row)

            val accessChecks = getBulkAccessGroupChecks(viewer, rowsByPostId)
            val tagsByPost = loadTagsForPosts(setOf(postId))
            val commentsByPost = loadCommentsForPosts(viewer, setOf(postId))
            val reactionsByPost = loadPostReactions(setOf(postId))

            toPostView(
                row = row,
                comments = commentsByPost[postId] ?: emptyList(),
                reactions = reactionsByPost[postId] ?: emptyList(),
                tags = tagsByPost[postId] ?: emptySet(),
                accessGroupChecks = accessChecks.getValue(postId)
            )
        }
    }

    override fun getPost(viewer: Viewer, diaryLogin: String, uri: String): PostPage {
        val userId = (viewer as? Viewer.Registered)?.userId

        val postView = transaction {
            val baseJoin = getBasicPostJoin()
            val q = baseJoin
                .slice(postBaseSlice)
                .select {
                    buildReadAccessCondition(viewer) and
                            (postDiary[Diaries.login] eq diaryLogin) and
                            (Posts.uri eq uri) and
                            (Posts.isArchived eq false)
                }
                .limit(1)

            val row = q.firstOrNull() ?: throw PostNotFoundException()

            if (userId != null) {
                notificationService.readAllPostNotifications(userId, row[Posts.id].value)
            }

            val rowsByPostId = mapOf(row[Posts.id].value to row)
            val postIds = rowsByPostId.keys

            val accessChecks = getBulkAccessGroupChecks(viewer, rowsByPostId)
            val tagsByPost = loadTagsForPosts(postIds)
            val commentsByPost = loadCommentsForPosts(viewer, postIds)
            val reactionsByPost = loadPostReactions(postIds)

            toPostView(
                row = row,
                comments = commentsByPost[row[Posts.id].value] ?: emptyList(),
                reactions = reactionsByPost[row[Posts.id].value] ?: emptyList(),
                tags = tagsByPost[row[Posts.id].value] ?: emptySet(),
                accessGroupChecks = accessChecks.getValue(row[Posts.id].value)
            )
        }

        val diary = getDiaryView(userId, diaryLogin)
        return PostPage(post = postView, diary = diary)
    }

    override fun getDiaryPosts(
        viewer: Viewer,
        diaryLogin: String,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDate?,
        to: LocalDate?,
        pageable: Pageable
    ): DiaryPage {
        val page = transaction {
            val params = PostSearchParams(viewer = viewer, authorLogin = null, diaryLogin = diaryLogin, text = text, tags = tags, from = from, to = to, isHidden = false)
            getPosts(params, emptyList(), pageable, Posts.isPreface to SortOrder.DESC, Posts.creationTime to pageable.direction)
        }

        val userId = (viewer as? Viewer.Registered)?.userId
        val diary = getDiaryView(userId, diaryLogin)
        return DiaryPage(diary = diary, posts = page)
    }

    private fun getDiaryView(userId: UUID?, diaryLogin: String): DiaryView {
        return transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            // Get all enabled styles from the diary's styleJunctions collection, sorted by ordinal
            val styleURLs = diaryEntity.styleJunctions
                .filter { it.enabled }
                .sortedBy { it.ordinal }
                .map { it.style.styleFile.toBlogFile() }
                .map { storageService.getFileURL(it) }

            val defaultGroups = if (diaryEntity.owner.value == userId) accessGroupService.getDefaultAccessGroups(userId, diaryLogin) else null
            DiaryView(
                name = diaryEntity.name,
                subtitle = diaryEntity.subtitle,
                styles = styleURLs,
                defaultGroups = defaultGroups,
            )
        }
    }

    override fun getPosts(
        viewer: Viewer,
        authorLogin: String?,
        diaryLogin: String?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDate?,
        to: LocalDate?,
        pageable: Pageable,
        vararg order: Pair<Expression<*>, SortOrder>
    ): Page<PostDto.View> {
        val params = PostSearchParams(viewer = viewer, authorLogin = authorLogin, diaryLogin = diaryLogin, text = text, tags = tags, from = from, to = to)
        return transaction {
            getPosts(params, emptyList(), pageable, *order)
        }
    }

    override fun addPost(userId: UUID, post: PostDto.Create): PostDto.View {
        return transaction {
            if (post.isPreface) {
                addPreface(userId, post)
            } else {
                addPostToDb(userId, post)
            }
        }
    }

    private fun addPreface(userId: UUID, post: PostDto.Create): PostDto.View {
        return transaction {
            val diaryId = DiaryEntity.find { Diaries.owner eq userId }.single().id
            val oldPreface = PostEntity.find { (Posts.diary eq diaryId) and (Posts.isPreface eq true) and (Posts.isArchived eq false) }.singleOrNull()

            if (oldPreface != null) {
                deletePost(oldPreface)
            }

            addPostToDb(userId, post)
        }
    }

    override fun getPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val params = PostSearchParams(viewer = viewer)
            getPosts(params, emptyList(), pageable,Posts.creationTime to SortOrder.DESC)
        }
    }

    override fun getDiscussedPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val params = PostSearchParams(viewer = viewer)
            getPosts(params, emptyList(), pageable,Posts.lastCommentTime to SortOrder.DESC_NULLS_LAST, Posts.creationTime to SortOrder.DESC)
        }
    }

    private fun Transaction.getPosts(params: PostSearchParams, conditions: List<Op<Boolean>> = emptyList(), pageable: Pageable, vararg order: Pair<Expression<*>, SortOrder>): Page<PostDto.View> {
        var query = buildPostQuery(params)
        for (condition in conditions) {
            query = query.apply { andWhere { condition } }
        }
        val rowsPage = executePaged(query, pageable, order)

        val rowsByPostId = rowsPage.rows.associateBy { it[Posts.id].value }
        val postIds = rowsByPostId.keys

        val accessChecks = getBulkAccessGroupChecks(params.viewer, rowsByPostId)
        val tagsByPost = loadTagsForPosts(postIds)
        val commentsByPost = loadCommentsForPosts(params.viewer, postIds)
        val reactionsByPost = loadPostReactions(postIds)

        val content = rowsByPostId.map { (postId, row) ->
            toPostView(
                row = row,
                comments = commentsByPost[postId] ?: emptyList(),
                reactions = reactionsByPost[postId] ?: emptyList(),
                tags = tagsByPost[postId] ?: emptySet(),
                accessGroupChecks = accessChecks.getValue(postId)
            )
        }.toList()

        return Page(content, rowsPage.currentPage, rowsPage.totalPages)
    }

    override fun getFollowedPosts(userId: UUID, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val followed = UserFollows
                .slice(UserFollows.following)
                .select { UserFollows.follower eq userId }
                .map { it[UserFollows.following].value }
                .toSet()

            if (followed.isEmpty()) {
                return@transaction Page(emptyList(), pageable.page, 0)
            }

            val params = PostSearchParams(viewer = Viewer.Registered(userId))
            val authorCondition = ((Posts.authorType eq PostAuthorType.LOCAL) and (localPostAuthor[Users.id] inList followed.toList())) or ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] inList followed.toList()))
            getPosts(params, listOf(authorCondition), pageable, Posts.creationTime to pageable.direction)
        }
    }

    override fun updatePost(userId: UUID, post: PostDto.Update): PostDto.View {
        return transaction {
            val postEntity = post.id.let { PostEntity.findById(it) } ?: throw PostNotFoundException()
            if (postEntity.authorId != userId) throw WrongUserException()

            val newUri = if ((post.uri.isNotEmpty() && post.uri != postEntity.uri) || (post.title != postEntity.title)) {
                checkOrCreateUri(postEntity.diaryId.value, post.title, post.uri)
            } else {
                postEntity.uri
            }

            val (readGroup, commentGroup, reactionGroup) = getReadAndCommentGroups(
                postEntity.diaryId.value,
                post.readGroupId,
                post.commentGroupId,
                post.reactionGroupId
            )

            val commentReactionGroup = validateCommentReactionGroup(postEntity.diaryId.value, post.commentReactionGroupId)

            postEntity.apply {
                uri = newUri

                avatar = post.avatar
                title = post.title
                text = post.text

                classes = post.classes

                isEncrypted = post.isEncrypted
                readGroupId = readGroup.id
                commentGroupId = commentGroup.id
                reactionGroupId = reactionGroup.id
                commentReactionGroupId = commentReactionGroup.id
            }
            updatePostTags(postEntity, post.tags)
            toPostView(Viewer.Registered(userId), postEntity)
        }
    }

    private fun validateCommentReactionGroup(diaryId: UUID, commentReactionGroupId: UUID): AccessGroupEntity {
        val commentReactionGroupEntity = AccessGroupEntity.findById(commentReactionGroupId) ?: throw InvalidAccessGroupException()
        val commentReactionGroupDiaryId = commentReactionGroupEntity.diaryId?.value
        if (commentReactionGroupDiaryId != null && commentReactionGroupDiaryId != diaryId) {
            throw InvalidAccessGroupException()
        }
        return commentReactionGroupEntity
    }

    private fun getReadAndCommentGroups(diaryId: UUID, readGroupId: UUID, commentGroupId: UUID, reactionGroupId: UUID): Triple<AccessGroupEntity, AccessGroupEntity, AccessGroupEntity> {
        val readGroupEntity = AccessGroupEntity.findById(readGroupId) ?: throw InvalidAccessGroupException()
        val readGroupDiaryId = readGroupEntity.diaryId?.value
        if (readGroupDiaryId != null && readGroupDiaryId != diaryId) {
            throw InvalidAccessGroupException()
        }

        val commentGroupEntity = AccessGroupEntity.findById(commentGroupId) ?: throw InvalidAccessGroupException()
        val commentGroupDiaryId = commentGroupEntity.diaryId?.value
        if (commentGroupDiaryId != null && commentGroupDiaryId != diaryId) {
            throw InvalidAccessGroupException()
        }

        val reactionGroupEntity = AccessGroupEntity.findById(reactionGroupId) ?: throw InvalidAccessGroupException()
        val reactionGroupDiaryId = reactionGroupEntity.diaryId?.value
        if (reactionGroupDiaryId != null && reactionGroupDiaryId != diaryId) {
            throw InvalidAccessGroupException()
        }

        return Triple(readGroupEntity, commentGroupEntity, reactionGroupEntity)
    }

    private fun updatePostTags(postEntity: PostEntity, newTags: Set<String>) {
        val existingTags = postEntity.tags
        val existingTagNames = existingTags.map { it.name }.toSet()

        val tagsToAdd = newTags.filter { it !in existingTagNames }
        val tagsToRemove = existingTags.filter { it.name !in newTags }.map { it.id }

        PostTags.deleteWhere { (post eq postEntity.id) and (tag inList tagsToRemove) }

        tagsToAdd.forEach { tag ->
            val tagId = getOrCreateTag(postEntity.diaryId.value, tag)
            PostTags.insert {
                it[PostTags.tag] = tagId
                it[post] = postEntity.id
            }
        }
    }

    override fun deletePost(userId: UUID, postId: UUID) {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: return@transaction
            if (postEntity.authorId != userId) throw WrongUserException()
            deletePost(postEntity)
        }
    }

    override fun hidePost(userId: UUID, postId: UUID) {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (postEntity.authorId != userId) throw WrongUserException()
            postEntity.isHidden = true
        }
    }

    override fun showPost(userId: UUID, postId: UUID) {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (postEntity.authorId != userId) throw WrongUserException()
            postEntity.isHidden = false
        }
    }


    override fun getComment(viewer: Viewer, commentId: UUID): CommentDto.View {
        return transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postEntity = PostEntity.findById(commentEntity.postId) ?: throw PostNotFoundException()
            val diaryEntity = DiaryEntity.findById(postEntity.diaryId.value) ?: throw InternalServerError()
            val diaryOwnerId = diaryEntity.owner.value
            val userId = (viewer as? Viewer.Registered)?.userId

            val isAuthorOfPost = (userId == postEntity.authorId)
            val isCommentOwner =
                when (commentEntity.authorType) {
                    CommentAuthorType.LOCAL -> userId == commentEntity.localAuthor?.value
                    CommentAuthorType.EXTERNAL -> {
                        val ext = commentEntity.externalAuthor?.let { ExternalUserEntity.findById(it) }
                        userId != null && ext?.user?.value == userId
                    }
                    CommentAuthorType.ANONYMOUS -> false
                }

            if (!isAuthorOfPost && !isCommentOwner &&
                !accessGroupService.inGroup(viewer, postEntity.readGroupId.value, diaryOwnerId)) {
                throw CommentNotFoundException()
            }

            val updated = commentEntity.toComment(this, viewer, accessGroupService, reactionService)
            updated
        }
    }

    override fun addComment(userId: UUID, comment: CommentDto.Create): CommentDto.View {
        val (commentEntity, comment) = transaction {
            val postEntity = PostEntity.findById(comment.postId) ?: throw PostNotFoundException()
            val diaryEntity = DiaryEntity.findById(postEntity.diaryId.value) ?: throw InternalServerError()
            val diaryOwnerId = diaryEntity.owner.value
            val viewer = Viewer.Registered(userId)
            if (userId != postEntity.authorId && (!accessGroupService.inGroup(viewer, postEntity.readGroupId.value, diaryOwnerId) || !accessGroupService.inGroup(viewer, postEntity.commentGroupId.value, diaryOwnerId))) {
                throw WrongUserException()
            }
            if (comment.parentCommentId != null) {
                val parentComment = CommentEntity.findById(comment.parentCommentId) ?: throw InvalidParentComment()
                if (parentComment.postId.value != comment.postId) throw InvalidParentComment()
            }
            val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()
            val commentId = Comments.insertAndGetId {
                it[post] = postEntity.id
                it[authorType] = CommentAuthorType.LOCAL
                it[localAuthor] = userId
                it[avatar] = comment.avatar
                it[text] = comment.text
                it[parentComment] = comment.parentCommentId
                it[reactionGroup] = comment.reactionGroupId?.let { groupId ->
                    // Verify the reaction group belongs to the diary
                    val groupEntity = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
                    if (groupEntity.diaryId?.value != postEntity.diaryId.value) {
                        throw InvalidAccessGroupException()
                    }
                    groupEntity.id
                } ?: postEntity.reactionGroupId
            }

            Posts.update({ Posts.id eq postEntity.id }) {
                it[lastCommentTime] = now
            }

            val postId = postEntity.id.value
            notificationService.subscribeToComments(userId, postId)
            notificationService.notifyAboutComment(commentId.value, userId, postId)

            val commentEntity = CommentEntity.findById(commentId)!!

            commentEntity to commentEntity.toComment(this, Viewer.Registered(userId), accessGroupService, reactionService)
        }
        commentWebSocketService.notifyCommentAdded(commentEntity)
        return comment
    }

    override fun updateComment(userId: UUID, comment: CommentDto.Update): CommentDto.View {
        return transaction {
            val commentEntity = CommentEntity.findById(comment.id) ?: throw CommentNotFoundException()
            if (userId != commentEntity.authorId) throw WrongUserException()
            commentEntity.apply {
                avatar = comment.avatar
                text = comment.text
            }

            commentWebSocketService.notifyCommentUpdated(commentEntity)

            commentEntity.toComment(this, Viewer.Registered(userId), accessGroupService, reactionService)
        }
    }

    // TODO allow post owner to delete comments
    override fun deleteComment(userId: UUID, commentId: UUID) {
        return transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            if (userId != commentEntity.authorId) throw WrongUserException()

            val postId = commentEntity.postId.value

            // Notify WebSocket clients about the deleted comment before deleting it
            commentWebSocketService.notifyCommentDeleted(commentId, postId)

            commentEntity.delete()

            // Find the most recent remaining comment for this post
            val latestComment = CommentEntity
                .find { Comments.post eq EntityID(postId, Comments) }
                .orderBy(Comments.creationTime to SortOrder.DESC)
                .firstOrNull()

            // Update post's lastCommentTime
            Posts.update({ Posts.id eq EntityID(postId, Posts) }) {
                it[lastCommentTime] = latestComment?.creationTime
            }
        }
    }

    private fun deletePost(postEntity: PostEntity) {
        postEntity.apply {
            isArchived = true
            uri = UUID.randomUUID().toString()
        }
    }

    private fun addPostToDb(userId: UUID, post: PostDto.Create): PostDto.View {
        return transaction {
            val diaryId = DiaryEntity.find { Diaries.owner eq userId }.first().id.value
            val postUri = checkOrCreateUri(diaryId, post.title, post.uri)

            val (readGroupEntity, commentGroupEntity, reactionGroupEntity) = getReadAndCommentGroups(
                diaryId,
                post.readGroupId,
                post.commentGroupId,
                post.reactionGroupId
            )

            val commentReactionGroupEntity = validateCommentReactionGroup(diaryId, post.commentReactionGroupId)
            val reactionSubsetEntity = post.reactionSubset?.let { ReactionSubsetEntity.findById(it) }
            if (reactionSubsetEntity != null) {
                if (reactionSubsetEntity.diary != diaryId) throw WrongUserException()
            }

            val postId = Posts.insertAndGetId {
                it[uri] = postUri

                it[diary] = diaryId
                it[authorType] = PostAuthorType.LOCAL
                it[localAuthor] = userId

                it[avatar] = post.avatar
                it[title] = post.title
                it[text] = post.text

                it[isPreface] = post.isPreface
                it[isEncrypted] = post.isEncrypted

                it[classes] = post.classes

                it[isArchived] = false
                it[isHidden] = false

                it[readGroup] = readGroupEntity.id
                it[commentGroup] = commentGroupEntity.id
                it[reactionGroup] = reactionGroupEntity.id
                it[commentReactionGroup] = commentReactionGroupEntity.id
                it[reactionSubset] = reactionSubsetEntity?.id
            }

            for (tag in post.tags) {
                val tagId = getOrCreateTag(diaryId, tag)
                PostTags.insert {
                    it[PostTags.tag] = tagId
                    it[PostTags.post] = postId
                }
            }

            notificationService.subscribeToComments(userId, postId.value)

            val postEntity = PostEntity.findById(postId)!!
            toPostView(Viewer.Registered(userId), postEntity)
        }
    }

    private fun getOrCreateTag(diaryId: UUID, tag: String): EntityID<UUID> {
        val tagEntity = TagEntity.find { (Tags.name eq tag) and (Tags.diary eq diaryId)}.firstOrNull()
        if (tagEntity != null) return tagEntity.id

        return Tags.insertAndGetId {
            it[name] = tag
            it[diary] = diaryId
        }
    }

    private fun isValidUri(uri: String): Boolean {
        return uri.matches(Regex("[a-zA-Z0-9-]+"))
    }

    private fun Transaction.checkOrCreateUri(diaryId: UUID, postTitle: String, postUri: String): String {
        return if (postUri.isBlank()) {
            createUri(diaryId, postTitle)
        } else {
            if (!isValidUri(postUri)) throw InvalidUriException()
            if (isUriBusy(diaryId, postUri)) throw UriIsBusyException()
            postUri
        }
    }

    private fun Transaction.createUri(diaryId: UUID, postTitle: String): String {
        val wordsPart = postTitle.lowercase().map { transliterate(it) }.joinToString("")
            .replace(Regex("[^-a-zA-Z0-9 ]"), "")
            .lowercase(Locale.getDefault())
            .split(Regex("\\s+"))
            .joinToString("-")
        if (wordsPart.isBlank()) {
            return UUID.randomUUID().toString()
        }

        if (!isUriBusy(diaryId, wordsPart)) {
            return wordsPart
        }

        var length = 3
        while (true) {
            val prefix = generateRandomAlphanumericString(length)
            val uri = "$prefix-$wordsPart"
            if (!isUriBusy(diaryId, uri)) {
                return uri
            }
            length++
        }
    }

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.isUriBusy(diaryId: UUID, uri: String): Boolean {
        return PostEntity.find { (Posts.diary eq diaryId) and (Posts.uri eq uri) }.firstOrNull() != null
    }

    // I don't want to info about the number of posts to be discovered, so...
    // But yes, it's an overcomplication kinda
    private fun generateRandomAlphanumericString(length: Int): String {
        val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    private fun toPostUpdateData(postEntity: PostEntity): PostDto.Update {
        return PostDto.Update(
            id = postEntity.id.value,
            uri = postEntity.uri,
            avatar = postEntity.avatar,

            title = postEntity.title,
            text = postEntity.text,

            readGroupId = postEntity.readGroupId.value,
            commentGroupId = postEntity.commentGroupId.value,
            reactionGroupId = postEntity.reactionGroupId.value,
            commentReactionGroupId = postEntity.reactionGroupId.value, // Using post's reaction group as default for comments

            tags = postEntity.tags.map { it.name }.toSet(),
            classes = postEntity.classes,

            isEncrypted = postEntity.isEncrypted,
        )
    }


    // TODO do not duplicate this method
    @Suppress("UnusedReceiverParameter")
    private fun Transaction.findDiaryByLogin(login: String): DiaryEntity {
        return DiaryEntity.find { Diaries.login eq login }.singleOrNull() ?: throw DiaryNotFoundException()
    }

    private fun transliterate(char: Char): String {
        if (!char.isCyrillic()) return char.toString()
        return when (char.lowercase()) {
            "а" -> "a"
            "б" -> "b"
            "в" -> "v"
            "г" -> "g"
            "д" -> "d"
            "е", "ё" -> "e"
            "ж" -> "zh"
            "з" -> "z"
            "и", "й" -> "i"
            "к" -> "k"
            "л" -> "l"
            "м" -> "m"
            "н" -> "n"
            "о" -> "o"
            "п" -> "p"
            "р" -> "r"
            "с" -> "s"
            "т" -> "t"
            "у" -> "u"
            "ф" -> "f"
            "х" -> "kh"
            "ц" -> "ts"
            "ч" -> "ch"
            "ш" -> "sh"
            "щ" -> "shch"
            "ы" -> "y"
            "э" -> "e"
            "ю" -> "yu"
            "я" -> "ya"
            else -> char.toString()
        }
    }

    private fun Char.isCyrillic(): Boolean {
        return this in 'а'..'я' || this in 'А'..'Я'
    }

    val postDiary = Diaries.alias("post_diary")
    val localPostAuthor = Users.alias("local_post_author")
    val externalPostAuthor = ExternalUsers.alias("external_post_author")
    val externalUserLinkedUser = Users.alias("external_user_linked_user")
    val localAuthorDiary = Diaries.alias("author_diary")

    val commentLocalAuthor = Users.alias("comment_local_author")
    val commentLocalAuthorDiary = Diaries.alias("comment_local_author_diary")
    val commentExternalAuthor = ExternalUsers.alias("comment_external_author")
    val commentExternalLinkedUser = Users.alias("comment_external_linked_user")
    val commentExternalLinkedUserDiary = Diaries.alias("comment_external_linked_user_diary")
    val commentAnonymousAuthor = AnonymousUsers.alias("comment_anonymous_author")

    private fun Transaction.toPostView(
        row: ResultRow,
        comments: List<CommentDto.View>,
        reactions: List<ReactionDto.ReactionInfo>,
        tags: Set<String>,
        accessGroupChecks: AccessChecks,
    ): PostDto.View {
        val authorType = row[Posts.authorType]

        val nickname: String
        val signature: String?
        val authorLogin: String?

        if (authorType == PostAuthorType.LOCAL) {
            nickname = row[localPostAuthor[Users.nickname]]
            signature = row[localPostAuthor[Users.signature]]
            authorLogin = row[localAuthorDiary[Diaries.login]]
        } else {
            val linkedUserId = row[externalPostAuthor[ExternalUsers.user]]
            if (linkedUserId != null) {
                nickname = row[externalUserLinkedUser[Users.nickname]]
                signature = row[externalUserLinkedUser[Users.signature]]
                authorLogin = row[localAuthorDiary[Diaries.login]]  // optional: or null
            } else {
                nickname = row[externalPostAuthor[ExternalUsers.nickname]]
                signature = null
                authorLogin = null
            }
        }

        return PostDto.View(
            id = row[Posts.id].value,
            uri = row[Posts.uri],

            avatar = row[Posts.avatar],
            authorNickname = nickname,
            authorLogin = authorLogin,
            authorSignature = signature,
            diaryLogin = row[postDiary[Diaries.login]],

            title = row[Posts.title],
            text = row[Posts.text],
            creationTime = row[Posts.creationTime],

            isPreface = row[Posts.isPreface],
            isEncrypted = row[Posts.isEncrypted],
            isHidden = row[Posts.isHidden],

            tags = tags,

            classes = row[Posts.classes],
            isCommentable = accessGroupChecks.canComment,
            comments = comments,

            readGroupId = row[Posts.readGroup].value,
            commentGroupId = row[Posts.commentGroup].value,
            reactionGroupId = row[Posts.reactionGroup].value,
            commentReactionGroupId = row[Posts.reactionGroup].value,
            isReactable = accessGroupChecks.canReact,
            reactions = reactions,
        )
    }

    private fun Transaction.getBulkAccessGroupChecks(
        viewer: Viewer,
        results: Map<UUID, ResultRow> // postId -> row
    ): Map<UUID, AccessChecks> {
        val userId = (viewer as? Viewer.Registered)?.userId

        val groupIdToOwnerId = mutableSetOf<Pair<UUID, UUID>>() // (groupId, diaryOwnerId)
        val postIdToAuthorUserId = mutableMapOf<UUID, UUID?>()

        results.forEach { (postId, row) ->
            val diaryOwnerId = row[postDiary[Diaries.owner]].value
            val commentGroupId = row[Posts.commentGroup].value
            val reactionGroupId = row[Posts.reactionGroup].value

            val authorType = row[Posts.authorType]
            val authorUserId = when (authorType) {
                PostAuthorType.LOCAL -> row[localPostAuthor[Users.id]].value
                PostAuthorType.EXTERNAL -> row[externalPostAuthor[ExternalUsers.user]]?.value
            }
            postIdToAuthorUserId[postId] = authorUserId

            if (userId != authorUserId) {
                groupIdToOwnerId.add(commentGroupId to diaryOwnerId)
                groupIdToOwnerId.add(reactionGroupId to diaryOwnerId)
            }
        }

        val groupResults = accessGroupService.bulkCheckGroups(viewer, groupIdToOwnerId)

        return results.mapValues { (postId, row) ->
            val diaryOwnerId = row[postDiary[Diaries.owner]].value
            val commentGroupId = row[Posts.commentGroup].value
            val reactionGroupId = row[Posts.reactionGroup].value
            val authorUserId = postIdToAuthorUserId[postId]

            val self = (userId == authorUserId)
            val canComment = self || (groupResults[commentGroupId to diaryOwnerId] ?: false)
            val canReact = self || (groupResults[reactionGroupId to diaryOwnerId] ?: false)

            AccessChecks(canComment, canReact)
        }
    }

    private fun getBasicPostJoin(): Join {
        return Posts
            .innerJoin(postDiary, { Posts.diary }, { postDiary[Diaries.id] })
            .leftJoin(localPostAuthor, { Posts.localAuthor }, { localPostAuthor[Users.id] })
            .leftJoin(externalPostAuthor, { Posts.externalAuthor }, { externalPostAuthor[ExternalUsers.id] })
            .leftJoin(externalUserLinkedUser, { externalPostAuthor[ExternalUsers.user] }, { externalUserLinkedUser[Users.id] })
            .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
            .leftJoin(localAuthorDiary, { localPostAuthor[Users.id] }, { localAuthorDiary[Diaries.owner] })
    }

    private data class AccessChecks(val canComment: Boolean, val canReact: Boolean)

    private data class PostSearchParams(
        val viewer: Viewer,
        val authorLogin: String? = null,
        val diaryLogin: String? = null,
        val text: String? = null,
        val tags: Pair<TagPolicy, Set<String>>? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val isHidden: Boolean? = null,
    )

    private data class PostRowsPage(
        val rows: List<ResultRow>,
        val currentPage: Int,
        val totalPages: Int,
    )

    private fun buildReadAccessCondition(viewer: Viewer): Op<Boolean> {
        return when (viewer) {
            is Viewer.Anonymous -> {
                AccessGroups.type eq AccessGroupType.EVERYONE
            }
            is Viewer.Registered -> {
                val uid = viewer.userId

                val isAuthor = (Posts.authorType eq PostAuthorType.LOCAL and (Posts.localAuthor eq uid)) or
                        (Posts.authorType eq PostAuthorType.EXTERNAL and (externalPostAuthor[ExternalUsers.user] eq uid))

                val customAccessSubquery = CustomGroupUsers
                    .slice(CustomGroupUsers.accessGroup)
                    .select { CustomGroupUsers.member eq uid }

                val friendsUsers = Friends.slice(Friends.user2)
                    .select { Friends.user1 eq uid }
                    .union(
                        Friends.slice(Friends.user1)
                            .select { Friends.user2 eq uid }
                    )

                isAuthor or
                        (AccessGroups.type eq AccessGroupType.EVERYONE) or
                        (AccessGroups.type eq AccessGroupType.REGISTERED_USERS) or
                        ((AccessGroups.type eq AccessGroupType.FRIENDS) and (postDiary[Diaries.owner] inSubQuery friendsUsers)) or
                        ((AccessGroups.type eq AccessGroupType.CUSTOM) and (Posts.readGroup inSubQuery customAccessSubquery))
            }
        }
    }

    private fun Query.andFilters(
        text: String?,
        authorLogin: String?,
        diaryLogin: String?,
        from: LocalDate?,
        to: LocalDate?,
        isHidden: Boolean?
    ): Query {
        return this.apply {
            andWhere {
                val base = (Posts.isArchived eq false) // and (Posts.isPreface eq false)
                var cond: Op<Boolean> = base

                if (text != null) {
                    cond = cond and (Posts.text.regexp(stringParam(text), false) or Posts.title.regexp(stringParam(text), false))
                }
                if (authorLogin != null) {
                    // автор = владелец дневника с login = authorLogin
                    val authorDiaryId = DiaryEntity.find { Diaries.login eq authorLogin }.firstOrNull()?.id
                    cond = if (authorDiaryId != null) {
                        cond and (localAuthorDiary[Diaries.id] eq authorDiaryId)
                    } else {
                        cond and Op.FALSE
                    }
                }
                if (diaryLogin != null) {
                    val diaryId = DiaryEntity.find { Diaries.login eq diaryLogin }.firstOrNull()?.id
                    cond = if (diaryId != null) cond and (Posts.diary eq diaryId) else cond and Op.FALSE
                }
                if (from != null) cond = cond and (Posts.creationTime greaterEq from.atTime(0, 0))
                if (to != null) cond = cond and (Posts.creationTime lessEq to.atTime(23, 59, 59))
                if (isHidden != null) cond = cond and (Posts.isHidden eq isHidden)

                cond
            }
        }
    }

    private fun Query.andTagFilter(tags: Pair<TagPolicy, Set<String>>?): Query {
        if (tags == null || tags.second.isEmpty()) return this
        val (policy, tagSet) = tags

        val unionSubquery = PostTags
            .innerJoin(Tags)
            .slice(PostTags.post)
            .select { Tags.name inList tagSet }
            .groupBy(PostTags.post)

        return when (policy) {
            TagPolicy.UNION -> {
                andWhere { Posts.id inSubQuery unionSubquery }
            }
            TagPolicy.INTERSECTION -> {
                val intersectionSubquery = unionSubquery
                    .having { Tags.name.count() eq tagSet.size.toLong() }
                andWhere { Posts.id inSubQuery intersectionSubquery }
            }
        }
    }

    // === Слайс колонок, достаточный для дальнейшей сборки DTO (и прав доступа) ===
    private val postBaseSlice: List<Expression<*>> = listOf(
        *Posts.columns.toTypedArray(),
        postDiary[Diaries.login], postDiary[Diaries.owner],

        localPostAuthor[Users.id], localPostAuthor[Users.nickname], localPostAuthor[Users.signature],
        externalPostAuthor[ExternalUsers.id], externalPostAuthor[ExternalUsers.user], externalPostAuthor[ExternalUsers.nickname],
        externalUserLinkedUser[Users.id], externalUserLinkedUser[Users.nickname], externalUserLinkedUser[Users.signature],

        localAuthorDiary[Diaries.login],
    )

    // === Билдер запроса постов ===
    private fun Transaction.buildPostQuery(params: PostSearchParams): Query {
        val baseJoin = getBasicPostJoin()
        val q = baseJoin
            .slice(postBaseSlice)
            .select { buildReadAccessCondition(params.viewer) }

        return q
            .andFilters(
                text = params.text,
                authorLogin = params.authorLogin,
                diaryLogin = params.diaryLogin,
                from = params.from,
                to = params.to,
                isHidden = params.isHidden,
            )
            .andTagFilter(params.tags)
    }

    private fun Transaction.executePaged(
        query: Query,
        pageable: Pageable,
        order: Array<out Pair<Expression<*>, SortOrder>>
    ): PostRowsPage {
        val totalCount = query.count()
        val totalPages = ceil(totalCount / pageable.size.toDouble()).toInt()
        val offset = (pageable.page - 1) * pageable.size

        val rows = query
            .orderBy(*order)
            .limit(pageable.size, offset.toLong())
            .toList()

        return PostRowsPage(rows, pageable.page, totalPages)
    }

    private fun Transaction.loadTagsForPosts(postIds: Set<UUID>): Map<UUID, Set<String>> {
        if (postIds.isEmpty()) return emptyMap()
        return PostTags
            .innerJoin(Tags)
            .slice(PostTags.post, Tags.name)
            .select { PostTags.post inList postIds.toList() }
            .groupBy { it[PostTags.post].value }
            .mapValues { (_, rows) -> rows.map { it[Tags.name] }.toSet() }
    }

    // TODO inReplyTo
    private fun Transaction.loadCommentsForPosts(
        viewer: Viewer,
        postIds: Set<UUID>
    ): Map<UUID, List<CommentDto.View>> {
        if (postIds.isEmpty()) return emptyMap()

        val postDiaryForComment = Diaries.alias("comment_post_diary")

        val rows = Comments
            // LOCAL
            .leftJoin(commentLocalAuthor, { Comments.localAuthor }, { commentLocalAuthor[Users.id] })
            .leftJoin(commentLocalAuthorDiary, { commentLocalAuthor[Users.id] }, { commentLocalAuthorDiary[Diaries.owner] })
            // EXTERNAL (+ linked local)
            .leftJoin(commentExternalAuthor, { Comments.externalAuthor }, { commentExternalAuthor[ExternalUsers.id] })
            .leftJoin(commentExternalLinkedUser, { commentExternalAuthor[ExternalUsers.user] }, { commentExternalLinkedUser[Users.id] })
            .leftJoin(commentExternalLinkedUserDiary, { commentExternalLinkedUser[Users.id] }, { commentExternalLinkedUserDiary[Diaries.owner] })
            // ANONYMOUS
            .leftJoin(commentAnonymousAuthor, { Comments.anonymousAuthor }, { commentAnonymousAuthor[AnonymousUsers.id] })
            // POST + его дневник
            .innerJoin(Posts, { Comments.post }, { Posts.id })
            .innerJoin(postDiaryForComment, { Posts.diary }, { postDiaryForComment[Diaries.id] })
            .slice(
                // comment
                Comments.id, Comments.post, Comments.authorType, Comments.localAuthor, Comments.externalAuthor, Comments.anonymousAuthor,
                Comments.avatar, Comments.text, Comments.creationTime, Comments.reactionGroup, Comments.parentComment,
                // post
                Posts.uri,
                postDiaryForComment[Diaries.login], postDiaryForComment[Diaries.owner],
                // local author
                commentLocalAuthor[Users.id], commentLocalAuthor[Users.nickname],
                commentLocalAuthorDiary[Diaries.login],
                // external author
                commentExternalAuthor[ExternalUsers.id], commentExternalAuthor[ExternalUsers.user], commentExternalAuthor[ExternalUsers.nickname],
                commentExternalLinkedUser[Users.id], commentExternalLinkedUser[Users.nickname],
                commentExternalLinkedUserDiary[Diaries.login],
                // anonymous author
                commentAnonymousAuthor[AnonymousUsers.id], commentAnonymousAuthor[AnonymousUsers.nickname]
            )
            .select { Comments.post inList postIds.toList() }
            .orderBy(Comments.creationTime to SortOrder.ASC)
            .toList()

        val viewerUserId = (viewer as? Viewer.Registered)?.userId
        val pairs = mutableSetOf<Pair<UUID, UUID>>() // (reactionGroupId, diaryOwnerId)
        rows.forEach { r ->
            val cmLocalAuthorId = r[Comments.localAuthor]?.value
            val isSelf = (viewerUserId != null && cmLocalAuthorId != null && viewerUserId == cmLocalAuthorId)
            if (!isSelf) {
                pairs.add(r[Comments.reactionGroup].value to r[postDiaryForComment[Diaries.owner]].value)
            }
        }
        val canReactByPair = pairs.associateWith { (groupId, ownerId) ->
            accessGroupService.inGroup(viewer, groupId, ownerId)
        }

        val parentIds = rows.mapNotNull { it[Comments.parentComment]?.value }.toSet()
        val replyMeta = loadReplyMeta(parentIds)

        val commentIds = rows.map { it[Comments.id].value }.toSet()
        val reactionsByComment = loadCommentReactions(commentIds)

        val byPost = linkedMapOf<UUID, MutableList<CommentDto.View>>()

        rows.forEach { r ->
            val postId = r[Comments.post].value
            val diaryLogin = r[postDiaryForComment[Diaries.login]]
            val postUri = r[Posts.uri]
            val diaryOwnerId = r[postDiaryForComment[Diaries.owner]].value

            // Автор
            val authorType = r[Comments.authorType]
            val (authorLogin: String?, authorNickname: String, isSelf: Boolean) = when (authorType) {
                CommentAuthorType.LOCAL -> {
                    val uid = r[commentLocalAuthor[Users.id]]?.value
                    val login = r[commentLocalAuthorDiary[Diaries.login]]
                    val nick = r[commentLocalAuthor[Users.nickname]]
                    Triple(login, nick, viewerUserId != null && uid != null && viewerUserId == uid)
                }
                CommentAuthorType.EXTERNAL -> {
                    val linkedUid = r[commentExternalAuthor[ExternalUsers.user]]?.value
                    if (linkedUid != null) {
                        val login = r[commentExternalLinkedUserDiary[Diaries.login]]
                        val nick = r[commentExternalLinkedUser[Users.nickname]]
                        Triple(login, nick, viewerUserId != null && viewerUserId == linkedUid)
                    } else {
                        val nick = r[commentExternalAuthor[ExternalUsers.nickname]]
                        Triple(null, nick, false)
                    }
                }
                CommentAuthorType.ANONYMOUS -> {
                    val nick = r[commentAnonymousAuthor[AnonymousUsers.nickname]]
                    Triple(null, nick, false)
                }
            }

            val canReact =
                isSelf || (canReactByPair[r[Comments.reactionGroup].value to diaryOwnerId] ?: false)

            val parentId = r[Comments.parentComment]?.value
            val inReply: CommentDto.ReplyView? = parentId?.let { pid ->
                val meta = replyMeta[pid]
                if (meta != null) CommentDto.ReplyView(id = pid, login = meta.login, nickname = meta.nickname)
                else CommentDto.ReplyView(id = pid, login = null, nickname = "unknown")
            }

            val view = CommentDto.View(
                id = r[Comments.id].value,
                authorLogin = authorLogin,
                authorNickname = authorNickname,
                postUri = postUri,
                diaryLogin = diaryLogin,
                avatar = r[Comments.avatar],
                text = r[Comments.text],
                creationTime = r[Comments.creationTime],
                isReactable = canReact,
                reactions = reactionsByComment[r[Comments.id].value] ?: emptyList(),
                reactionGroupId = r[Comments.reactionGroup].value,
                inReplyTo = inReply
            )
            byPost.getOrPut(postId) { mutableListOf() }.add(view)
        }
        return byPost
    }

    private fun Transaction.loadPostReactions(postIds: Set<UUID>): Map<UUID, List<ReactionDto.ReactionInfo>> {
        if (postIds.isEmpty()) return emptyMap()

        // 1) Зарегистрированные реакции с пользователями (Users -> Diaries по owner)
        data class RegRow(
            val postId: UUID,
            val reactionId: UUID,
            val userLogin: String,
            val userNickname: String
        )

        val regRows: List<RegRow> =
            (PostReactions
                .innerJoin(Reactions)                          // FK: PostReactions.reaction -> Reactions.id
                .innerJoin(Files)                              // FK: Reactions.icon -> Files.id
                .innerJoin(Users, { PostReactions.user }, { Users.id })
                .innerJoin(Diaries, { Users.id }, { Diaries.owner }))
                .slice(PostReactions.post, Reactions.id, Diaries.login, Users.nickname)
                .select { PostReactions.post inList postIds.toList() }
                .map {
                    RegRow(
                        postId = it[PostReactions.post].value,
                        reactionId = it[Reactions.id].value,
                        userLogin = it[Diaries.login],
                        userNickname = it[Users.nickname]
                    )
                }

        // 2) Анонимные реакции (агрегаты)
        val anonCounts: Map<Pair<UUID, UUID>, Int> =
            AnonymousPostReactions
                .slice(AnonymousPostReactions.post, AnonymousPostReactions.reaction, AnonymousPostReactions.reaction.count())
                .select { AnonymousPostReactions.post inList postIds.toList() }
                .groupBy(AnonymousPostReactions.post, AnonymousPostReactions.reaction)
                .associate { r ->
                    (r[AnonymousPostReactions.post].value to r[AnonymousPostReactions.reaction].value) to r[AnonymousPostReactions.reaction.count()].toInt()
                }

        // 3) Мета реакций (имя + iconUrl) для всего множества reactionId (из рег. и анонимов)
        val reactionIds: Set<UUID> =
            regRows.map { it.reactionId }.toSet() + anonCounts.keys.map { it.second }.toSet()

        val rxMeta: Map<UUID, Pair<String /*name*/, String /*iconUrl*/>> =
            if (reactionIds.isEmpty()) emptyMap() else
                (Reactions innerJoin Files)
                    .slice(Reactions.id, Reactions.name, Files.id)
                    .select { Reactions.id inList reactionIds.toList() }
                    .associate { row ->
                        val fileId = row[Files.id].value
                        row[Reactions.id].value to (
                                row[Reactions.name] to storageService.getFileURL(
                                    FileEntity.findById(fileId)!!.toBlogFile()
                                )
                                )
                    }

        // 4) Группируем зарегистрированных: post -> reaction -> users
        val usersByPostReaction: Map<UUID, Map<UUID, List<ReactionDto.UserInfo>>> =
            regRows.groupBy { it.postId }.mapValues { (_, list) ->
                list.groupBy { it.reactionId }.mapValues { (_, rlist) ->
                    rlist.map { ReactionDto.UserInfo(login = it.userLogin, nickname = it.userNickname) }
                }
            }

        // 5) Сборка результата
        val result = mutableMapOf<UUID, MutableMap<UUID, ReactionDto.ReactionInfo>>()
        postIds.forEach { result[it] = mutableMapOf() }

        // Зарегистрированные + анонимы
        usersByPostReaction.forEach { (postId, byReaction) ->
            val perPost = result.getValue(postId)
            byReaction.forEach { (reactionId, users) ->
                val (name, iconUrl) = rxMeta.getValue(reactionId)
                val anon = anonCounts[postId to reactionId] ?: 0
                perPost[reactionId] = ReactionDto.ReactionInfo(
                    id = reactionId,
                    name = name,
                    iconUri = iconUrl,
                    count = users.size + anon,
                    users = users,
                    anonymousCount = anon
                )
            }
        }

        // Реакции, встречающиеся только у анонимов
        anonCounts.forEach { (k, anon) ->
            val (postId, reactionId) = k
            val perPost = result.getValue(postId)
            if (perPost[reactionId] == null) {
                val (name, iconUrl) = rxMeta.getValue(reactionId)
                perPost[reactionId] = ReactionDto.ReactionInfo(
                    id = reactionId,
                    name = name,
                    iconUri = iconUrl,
                    count = anon,
                    users = emptyList(),
                    anonymousCount = anon
                )
            }
        }

        return result.mapValues { (_, map) -> map.values.toList() }
    }

    private fun Transaction.loadCommentReactions(commentIds: Set<UUID>): Map<UUID, List<ReactionDto.ReactionInfo>> {
        if (commentIds.isEmpty()) return emptyMap()

        // Зарегистрированные пользователи
        val reg = (CommentReactions
            .innerJoin(Reactions)
            .innerJoin(Files)
            .innerJoin(Users, { CommentReactions.user }, { Users.id })
            .innerJoin(Diaries, { Users.id }, { Diaries.owner })
                )
            .slice(
                CommentReactions.comment, Reactions.id, Reactions.name, Files.id,
                Diaries.login, Users.nickname
            )
            .select { CommentReactions.comment inList commentIds.toList() }
            .toList()

        // Анонимы: агрегаты
        val anonCounts = AnonymousCommentReactions
            .slice(AnonymousCommentReactions.comment, AnonymousCommentReactions.reaction, AnonymousCommentReactions.reaction.count())
            .select { AnonymousCommentReactions.comment inList commentIds.toList() }
            .groupBy(AnonymousCommentReactions.comment, AnonymousCommentReactions.reaction)
            .associate { row ->
                (row[AnonymousCommentReactions.comment].value to row[AnonymousCommentReactions.reaction].value) to row[AnonymousCommentReactions.reaction.count()].toInt()
            }

        // Иконки (уникальные по реакции)
        val reactionToFile = reg
            .map { it[Reactions.id].value to it[Files.id].value }
            .distinctBy { it.first }
            .associate { it.first to it.second }

        val reactionToIconUrl = reactionToFile.mapValues { (_, fileId) ->
            storageService.getFileURL(FileEntity.findById(fileId)!!.toBlogFile())
        }

        // Группировка: comment -> reaction -> users
        val byCommentReaction = reg.groupBy { it[CommentReactions.comment].value }.mapValues { (_, rows) ->
            rows.groupBy { it[Reactions.id].value }.mapValues { (_, rlist) ->
                rlist.map {
                    ReactionDto.UserInfo(
                        login = it[Diaries.login],
                        nickname = it[Users.nickname]
                    )
                }
            }
        }

        val out = mutableMapOf<UUID, MutableMap<UUID, ReactionDto.ReactionInfo>>()

        commentIds.forEach { cid -> out[cid] = mutableMapOf() }

        byCommentReaction.forEach { (cid, byRx) ->
            val perComment = out.getValue(cid)
            byRx.forEach { (rxId, users) ->
                val name = reg.first { it[Reactions.id].value == rxId }[Reactions.name]
                val iconUrl = reactionToIconUrl.getValue(rxId)
                val anon = anonCounts[cid to rxId] ?: 0
                perComment[rxId] = ReactionDto.ReactionInfo(
                    id = rxId,
                    name = name,
                    iconUri = iconUrl,
                    count = users.size + anon,
                    users = users,
                    anonymousCount = anon
                )
            }
        }

        // Реакции только от анонимов
        anonCounts.forEach { (k, anon) ->
            val (cid, rxId) = k
            val perComment = out.getValue(cid)
            if (perComment[rxId] == null) {
                val rx = Reactions.innerJoin(Files)
                    .slice(Reactions.id, Reactions.name, Files.id)
                    .select { Reactions.id eq rxId }
                    .firstOrNull() ?: return@forEach
                val iconUrl = storageService.getFileURL(FileEntity.findById(rx[Files.id].value)!!.toBlogFile())
                perComment[rxId] = ReactionDto.ReactionInfo(
                    id = rxId,
                    name = rx[Reactions.name],
                    iconUri = iconUrl,
                    count = anon,
                    users = emptyList(),
                    anonymousCount = anon
                )
            }
        }

        return out.mapValues { (_, map) -> map.values.toList() }
    }

    private data class ReplyMeta(val login: String?, val nickname: String)

    private fun Transaction.loadReplyMeta(parentIds: Set<UUID>): Map<UUID, ReplyMeta> {
        if (parentIds.isEmpty()) return emptyMap()

        val rows = Comments
            .leftJoin(Users, { Comments.localAuthor }, { Users.id })
            .leftJoin(Diaries, { Users.id }, { Diaries.owner })
            .leftJoin(ExternalUsers, { Comments.externalAuthor }, { ExternalUsers.id })
            .leftJoin(Users.alias("reply_ext_link_user"), { ExternalUsers.user }, { Users.alias("reply_ext_link_user")[Users.id] })
            .leftJoin(Diaries.alias("reply_ext_link_diary"),
                { Users.alias("reply_ext_link_user")[Users.id] },
                { Diaries.alias("reply_ext_link_diary")[Diaries.owner] }
            )
            .leftJoin(AnonymousUsers, { Comments.anonymousAuthor }, { AnonymousUsers.id })
            .slice(
                Comments.id, Comments.authorType,
                Users.nickname, Diaries.login,
                ExternalUsers.nickname,
                Users.alias("reply_ext_link_user")[Users.nickname],
                Diaries.alias("reply_ext_link_diary")[Diaries.login],
                AnonymousUsers.nickname
            )
            .select { Comments.id inList parentIds.toList() }
            .toList()

        return rows.associate { r ->
            val authorType = r[Comments.authorType]
            val (login: String?, nickname: String) = when (authorType) {
                CommentAuthorType.LOCAL ->
                    r[Diaries.login] to r[Users.nickname]
                CommentAuthorType.EXTERNAL -> {
                    val linkedLogin = r[Diaries.alias("reply_ext_link_diary")[Diaries.login]]
                    val linkedNick = r[Users.alias("reply_ext_link_user")[Users.nickname]]
                    linkedLogin to linkedNick
                }
                CommentAuthorType.ANONYMOUS ->
                    null to r[AnonymousUsers.nickname]
            }
            r[Comments.id].value to ReplyMeta(login = login, nickname = nickname)
        }
    }

    // Публичный фасад: из PostEntity в View одним вызовом, без N+1
    fun toPostView(viewer: Viewer, postEntity: PostEntity): PostDto.View = transaction {
        toPostViewById(viewer, postEntity.id.value)
    }

    // Внутренний конвейер для одного поста (один SELECT по базовому JOIN + пакетные догрузки)
    private fun Transaction.toPostViewById(viewer: Viewer, postId: UUID): PostDto.View {
        val baseJoin = getBasicPostJoin()

        val row = baseJoin
            .slice(postBaseSlice)
            .select { Posts.id eq EntityID(postId, Posts) }
            .limit(1)
            .firstOrNull() ?: throw PostNotFoundException()

        val rowsByPostId = mapOf(postId to row)

        val accessChecks = getBulkAccessGroupChecks(viewer, rowsByPostId)
        val tagsByPost = loadTagsForPosts(setOf(postId))
        val commentsByPost = loadCommentsForPosts(viewer, setOf(postId))
        val reactionsByPost = loadPostReactions(setOf(postId))

        return toPostView(
            row = row,
            comments = commentsByPost[postId] ?: emptyList(),
            reactions = reactionsByPost[postId] ?: emptyList(),
            tags = tagsByPost[postId] ?: emptySet(),
            accessGroupChecks = accessChecks.getValue(postId)
        )
    }
}
