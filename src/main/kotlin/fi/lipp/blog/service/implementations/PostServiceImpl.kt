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
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder
import java.util.*
import kotlin.random.Random

class PostServiceImpl(
    private val accessGroupService: AccessGroupService,
    private val storageService: StorageService,
    private val reactionService: ReactionService,
    private val notificationService: NotificationService,
    private val commentWebSocketService: CommentWebSocketService
) : PostService {
    private val postQueryHelper = PostQueryHelper()
    private val reactionLoader = ReactionLoader(storageService)
    private val commentViewLoader = CommentViewLoader(accessGroupService, reactionLoader)
    private val postViewLoader = PostViewLoader(accessGroupService, postQueryHelper, reactionLoader)

    override fun getPostForEdit(userId: UUID, postId: UUID): PostDto.Update {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (!postEntity.isOwnedBy(userId)) throw WrongUserException()
            return@transaction toPostUpdateData(postEntity)
        }
    }

    override fun getPreface(viewer: Viewer, diaryLogin: String): PostDto.View? {
        return transaction {
            val row = postQueryHelper.loadSinglePostRow {
                (postQueryHelper.postDiary[Diaries.login] eq diaryLogin) and
                    (Posts.isPreface eq true) and
                    (Posts.isArchived eq false)
            } ?: return@transaction null

            val diaryOwnerId = row[postQueryHelper.postDiary[Diaries.owner]].value
            if (!postViewLoader.canReadPost(viewer, row, diaryOwnerId)) return@transaction null

            postViewLoader.toPostView(this, viewer, row)
        }
    }

    override fun getPost(viewer: Viewer, diaryLogin: String, uri: String): PostPage {
        val userId = (viewer as? Viewer.Registered)?.userId

        val postAndComments = transaction {
            val row = postQueryHelper.loadSinglePostRow {
                    postQueryHelper.buildReadAccessCondition(viewer) and
                        (postQueryHelper.postDiary[Diaries.login] eq diaryLogin) and
                        (Posts.uri eq uri) and
                        (Posts.isArchived eq false)
            } ?: throw PostNotFoundException()

            if (userId != null) {
                notificationService.readAllPostNotifications(userId, row[Posts.id].value)
            }

            val postId = row[Posts.id].value
            val postView = postViewLoader.toPostView(this, viewer, row)
            val comments = commentViewLoader.loadCommentsForPosts(this, viewer, setOf(postId))[postId] ?: emptyList()
            postView to comments
        }

        val diary = getDiaryView(userId, diaryLogin)
        return PostPage(post = postAndComments.first, diary = diary, comments = postAndComments.second)
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
            val params = PostQueryHelper.PostSearchParams(
                viewer = viewer,
                authorLogin = null,
                diaryLogin = diaryLogin,
                text = text,
                tags = tags,
                from = from,
                to = to,
                isHidden = false
            )
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
                .let { styleFiles ->
                    val urlsById = storageService.getFileURLs(styleFiles)
                    styleFiles.map { urlsById.getValue(it.id) }
                }

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
        isHidden: Boolean?,
        pageable: Pageable,
        vararg order: Pair<Expression<*>, SortOrder>
    ): Page<PostDto.View> {
        val params = PostQueryHelper.PostSearchParams(
            viewer = viewer,
            authorLogin = authorLogin,
            diaryLogin = diaryLogin,
            text = text,
            tags = tags,
            from = from,
            to = to,
            isHidden = isHidden
        )
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
            val params = PostQueryHelper.PostSearchParams(viewer = viewer)
            getPosts(params, emptyList(), pageable,Posts.creationTime to SortOrder.DESC)
        }
    }

    override fun getDiscussedPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val params = PostQueryHelper.PostSearchParams(viewer = viewer)
            getPosts(params, emptyList(), pageable,Posts.lastCommentTime to SortOrder.DESC_NULLS_LAST, Posts.creationTime to SortOrder.DESC)
        }
    }
    
    override fun getHiddenPosts(userId: UUID, diaryLogin: String, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val diary = findDiaryByLogin(diaryLogin)
            if (diary.owner.value != userId) throw WrongUserException()

            val params = PostQueryHelper.PostSearchParams(
                viewer = Viewer.Registered(userId),
                diaryLogin = diaryLogin,
                isHidden = true
            )
            getPosts(params, emptyList(), pageable, Posts.creationTime to pageable.direction)
        }
    }

    private fun Transaction.getPosts(params: PostQueryHelper.PostSearchParams, conditions: List<Op<Boolean>> = emptyList(), pageable: Pageable, vararg order: Pair<Expression<*>, SortOrder>): Page<PostDto.View> {
        var query = postQueryHelper.buildPostQuery(params)
        for (condition in conditions) {
            query = query.apply { andWhere { condition } }
        }
        val rowsPage = postQueryHelper.executePaged(query, pageable, order)

        val rowsByPostId = rowsPage.rows.associateBy { it[Posts.id].value }
        val dependencies = postViewLoader.loadPostViewDependencies(this, params.viewer, rowsByPostId)

        val content = rowsByPostId.map { (postId, row) ->
            postViewLoader.toPostView(
                row = row,
                commentsCount = dependencies.commentCountsByPost[postId] ?: 0,
                reactions = dependencies.reactionsByPost[postId] ?: emptyList(),
                tags = dependencies.tagsByPost[postId] ?: emptySet(),
                accessGroupChecks = dependencies.accessChecks.getValue(postId)
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

            val params = PostQueryHelper.PostSearchParams(viewer = Viewer.Registered(userId))
            val authorCondition = postQueryHelper.authorMatchesUsers(followed.toList())
            getPosts(params, listOf(authorCondition), pageable, Posts.creationTime to pageable.direction)
        }
    }

    override fun getFriendsPosts(userId: UUID, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val friends = Friends
                .select { (Friends.user1 eq userId) or (Friends.user2 eq userId) }
                .map {
                    if (it[Friends.user1].value == userId) it[Friends.user2].value else it[Friends.user1].value
                }
                .toSet()

            if (friends.isEmpty()) {
                return@transaction Page(emptyList(), pageable.page, 0)
            }

            val params = PostQueryHelper.PostSearchParams(viewer = Viewer.Registered(userId))
            val authorCondition = postQueryHelper.authorMatchesUsers(friends.toList())
            getPosts(params, listOf(authorCondition), pageable, Posts.creationTime to pageable.direction)
        }
    }

    override fun updatePost(userId: UUID, post: PostDto.Update): PostDto.View {
        return transaction {
            val postEntity = post.id.let { PostEntity.findById(it) } ?: throw PostNotFoundException()
            if (!postEntity.isOwnedBy(userId)) throw WrongUserException()

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
                isHidden = post.isHidden
                readGroupId = readGroup.id
                commentGroupId = commentGroup.id
                reactionGroupId = reactionGroup.id
                commentReactionGroupId = commentReactionGroup.id
            }
            updatePostTags(postEntity, post.tags)
            postViewLoader.toPostView(this, Viewer.Registered(userId), postEntity)
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
            if (!postEntity.isOwnedBy(userId)) throw WrongUserException()
            deletePost(postEntity)
        }
    }

    override fun hidePost(userId: UUID, postId: UUID) {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (!postEntity.isOwnedBy(userId)) throw WrongUserException()
            postEntity.isHidden = true
        }
    }

    override fun showPost(userId: UUID, postId: UUID) {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (!postEntity.isOwnedBy(userId)) throw WrongUserException()
            postEntity.isHidden = false
        }
    }


    override fun getComment(viewer: Viewer, commentId: UUID): CommentDto.View {
        return transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postEntity = commentEntity.post
            val diaryOwnerId = postEntity.diary.owner.value
            val userId = (viewer as? Viewer.Registered)?.userId

            val isAuthorOfPost = userId != null && postEntity.isOwnedBy(userId)
            val isCommentOwner = userId != null && commentEntity.isOwnedBy(userId)

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
            val diaryOwnerId = postEntity.diary.owner.value
            val viewer = Viewer.Registered(userId)
            if (!postEntity.isOwnedBy(userId) && (!accessGroupService.inGroup(viewer, postEntity.readGroupId.value, diaryOwnerId) || !accessGroupService.inGroup(viewer, postEntity.commentGroupId.value, diaryOwnerId))) {
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
            }

            val dependencyUserIds = buildSet {
                add(userId)
                postEntity.authorId?.let(::add)

                if (comment.parentCommentId != null) {
                    addAll(
                        CommentDependencies
                            .select { CommentDependencies.comment eq comment.parentCommentId }
                            .map { it[CommentDependencies.user].value }
                    )
                }
            }

            CommentDependencies.batchInsert(dependencyUserIds) { dependencyUserId ->
                this[CommentDependencies.comment] = commentId
                this[CommentDependencies.user] = dependencyUserId
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
            if (!commentEntity.isOwnedBy(userId)) throw WrongUserException()
            commentEntity.apply {
                avatar = comment.avatar
                text = comment.text
            }

            commentWebSocketService.notifyCommentUpdated(commentEntity)

            commentEntity.toComment(this, Viewer.Registered(userId), accessGroupService, reactionService)
        }
    }

    override fun deleteComment(userId: UUID, commentId: UUID) {
        return transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            val postId = commentEntity.postId.value
            val postEntity = commentEntity.post

            if (!commentEntity.isOwnedBy(userId) && !postEntity.isOwnedBy(userId)) throw WrongUserException()

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
                it[isHidden] = post.isHidden

                it[classes] = post.classes

                it[isArchived] = false

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
            postViewLoader.toPostView(this, Viewer.Registered(userId), postEntity)
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
            commentReactionGroupId = postEntity.commentReactionGroupId.value,

            tags = postEntity.tags.map { it.name }.toSet(),
            classes = postEntity.classes,

            isEncrypted = postEntity.isEncrypted,
            isHidden = postEntity.isHidden,
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

    private fun PostEntity.isOwnedBy(userId: UUID): Boolean {
        return getEffectiveAuthor()?.id?.value == userId
    }

    private fun CommentEntity.isOwnedBy(userId: UUID): Boolean {
        return getEffectiveAuthor()?.id?.value == userId
    }
}
