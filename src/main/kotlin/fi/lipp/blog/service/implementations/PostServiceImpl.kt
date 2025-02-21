package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.ResultRow
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random

class PostServiceImpl(
    private val accessGroupService: AccessGroupService,
    private val storageService: StorageService,
    private val reactionService: ReactionService,
    private val notificationService: NotificationService
) : PostService {
    override fun getPostForEdit(userId: UUID, postId: UUID): PostDto.Update {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId) throw WrongUserException()
            return@transaction toPostUpdateData(postEntity)
        }
    }

    override fun getPreface(viewer: Viewer, diaryLogin: String): PostDto.View? {
        val userId = (viewer as? Viewer.Registered)?.userId
        return transaction {
            val diaryId = findDiaryByLogin(diaryLogin).id
            val preface = PostEntity.find { (Posts.diary eq diaryId) and (Posts.isPreface eq true) and (Posts.isArchived eq false) }.firstOrNull() ?: return@transaction null
            return@transaction if (userId == preface.authorId.value || accessGroupService.inGroup(viewer, preface.readGroupId.value)) {
                toPostView(viewer, preface)
            } else {
                null
            }
        }
    }

    override fun getPost(viewer: Viewer, diaryLogin: String, uri: String): PostDto.View {
        val userId = (viewer as? Viewer.Registered)?.userId
        return transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            val postEntity = PostEntity.find { (Posts.diary eq diaryEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            if (userId == postEntity.authorId.value || accessGroupService.inGroup(viewer, postEntity.readGroupId.value)) {
                if (userId != null) notificationService.readAllPostNotifications(userId, postEntity.id.value)
                toPostView(viewer, postEntity)
            } else {
                throw PostNotFoundException()
            }
        }
    }

    override fun getPosts(
        viewer: Viewer,
        authorLogin: String?,
        diaryLogin: String?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<PostDto.View> {
        val userId = (viewer as? Viewer.Registered)?.userId
        return transaction {
            val diaryId = diaryLogin?.let { DiaryEntity.find { Diaries.login eq it }.firstOrNull()?.id }
            val authorId = if (authorLogin != null) {
                val authorDiary = DiaryEntity.find { Diaries.login eq authorLogin }.firstOrNull()
                authorDiary?.owner
            } else {
                null
            }
            val query = Posts
                .innerJoin(Diaries)
                .innerJoin(Users, { Posts.author }, { Users.id })
                .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
                .leftJoin(PostTags)
                .leftJoin(Tags)
                .slice(Posts.columns + Users.id + Users.nickname + Diaries.login + AccessGroups.type)
                .select {
                    val baseCondition = (Posts.isArchived eq false) and (Posts.isPreface eq false)

                    val accessCondition = when {
                        userId != null -> {
                            val customAccessSubquery = CustomGroupUsers
                                .slice(CustomGroupUsers.accessGroup)
                                .select { CustomGroupUsers.member eq userId }

                            (Posts.author eq userId) or
                            (AccessGroups.type eq AccessGroupType.EVERYONE) or
                            (AccessGroups.type eq AccessGroupType.REGISTERED_USERS) or
                            (AccessGroups.type eq AccessGroupType.CUSTOM and Posts.readGroup.inSubQuery(customAccessSubquery))
                        }
                        else -> (AccessGroups.type eq AccessGroupType.EVERYONE)
                    }

                    baseCondition and accessCondition
                }
                .apply {
                    text?.let { andWhere { Posts.text.regexp(stringParam(text), false) or Posts.title.regexp(stringParam(text), false) } }
                    authorId?.let { andWhere { Posts.author eq authorId } }
                    diaryId?.let { andWhere { Posts.diary eq diaryId } }
                    from?.let { andWhere { Posts.creationTime greaterEq it } }
                    to?.let { andWhere { Posts.creationTime lessEq it } }
                }
                .apply {
                    tags?.let { (policy, tagSet) ->
                        val unionSubquery = PostTags
                            .innerJoin(Tags)
                            .slice(PostTags.post)
                            .select { Tags.name inList tagSet }
                            .groupBy(PostTags.post)
                        when (policy) {
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
                }
                .orderBy(Posts.creationTime to pageable.direction)
                .groupBy(Posts.id)

            val totalCount = query.count()
            val totalPages = ceil(totalCount / pageable.size.toDouble()).toInt()
            val offset = (pageable.page - 1) * pageable.size

            val results = query
                .limit(pageable.size, offset.toLong())
                .map { row -> toPostView(viewer, row) }
            Page(results, pageable.page, totalPages)
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
        val preface = PostEntity.find { (Posts.author eq userId) and (Posts.isPreface eq true) }.firstOrNull()
        if (preface != null) {
            deletePost(preface)
        }

        return addPostToDb(userId, post)
    }

    override fun getPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val baseQuery = Posts
                .innerJoin(Users, { Posts.author }, { Users.id })
                .innerJoin(Diaries, { Posts.diary }, { Diaries.id })
                .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
                .slice(Posts.columns + Users.id + Users.nickname + Diaries.login + AccessGroups.type)
                .select {
                    val accessCondition = when (viewer) {
                        is Viewer.Anonymous -> {
                            AccessGroups.type eq AccessGroupType.EVERYONE
                        }
                        is Viewer.Registered -> {
                            val customAccessSubquery = CustomGroupUsers
                                .slice(CustomGroupUsers.accessGroup)
                                .select { CustomGroupUsers.member eq viewer.userId }

                            (Posts.author eq viewer.userId) or // Owner can see all their posts
                            (AccessGroups.type eq AccessGroupType.EVERYONE) or
                            (AccessGroups.type eq AccessGroupType.REGISTERED_USERS) or
                            (AccessGroups.type eq AccessGroupType.CUSTOM and Posts.readGroup.inSubQuery(customAccessSubquery))
                        }
                    }

                    accessCondition and
                    (Posts.isArchived eq false) and
                    (Posts.isPreface eq false)
                }

            val total = baseQuery.count()
            val totalPages = ceil(total.toDouble() / pageable.size).toInt()

            val content = baseQuery
                .orderBy(Posts.creationTime to SortOrder.DESC)
                .limit(pageable.size, (pageable.page * pageable.size).toLong())
                .map { row: ResultRow -> toPostView(viewer, row) }

            Page(
                content = content,
                currentPage = pageable.page,
                totalPages = totalPages
            )
        }
    }

    override fun getDiscussedPosts(viewer: Viewer, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val baseQuery = Posts
                .innerJoin(Users, { Posts.author }, { Users.id })
                .innerJoin(Diaries, { Posts.diary }, { Diaries.id })
                .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
                .slice(Posts.columns + Users.id + Users.nickname + Diaries.login + AccessGroups.type)
                .select {
                    val accessCondition = when (viewer) {
                        is Viewer.Anonymous -> {
                            AccessGroups.type eq AccessGroupType.EVERYONE
                        }
                        is Viewer.Registered -> {
                            val customAccessSubquery = CustomGroupUsers
                                .slice(CustomGroupUsers.accessGroup)
                                .select { CustomGroupUsers.member eq viewer.userId }

                            (Posts.author eq viewer.userId) or // Owner can see all their posts
                            (AccessGroups.type eq AccessGroupType.EVERYONE) or
                            (AccessGroups.type eq AccessGroupType.REGISTERED_USERS) or
                            (AccessGroups.type eq AccessGroupType.CUSTOM and Posts.readGroup.inSubQuery(customAccessSubquery))
                        }
                    }

                    accessCondition and
                    (Posts.isArchived eq false) and
                    (Posts.isPreface eq false)
                }

            val total = baseQuery.count()
            val totalPages = ceil(total.toDouble() / pageable.size).toInt()

            val content = baseQuery
                .orderBy(
                    Posts.lastCommentTime to SortOrder.DESC_NULLS_LAST,
                    Posts.creationTime to SortOrder.DESC
                )
                .limit(pageable.size, (pageable.page * pageable.size).toLong())
                .map { row: ResultRow -> toPostView(viewer, row) }

            Page(
                content = content,
                currentPage = pageable.page,
                totalPages = totalPages
            )
        }
    }

    override fun getFollowedPosts(userId: UUID, pageable: Pageable): Page<PostDto.View> {
        return transaction {
            val query = Posts
                .innerJoin(Diaries)
                .innerJoin(Users, { Posts.author }, { Users.id })
                .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
                .innerJoin(UserFollows, { Posts.author }, { UserFollows.following })
                .slice(Posts.columns + Users.id + Users.nickname + Diaries.login + AccessGroups.type)
                .select {
                    val baseCondition = (Posts.isArchived eq false) and (Posts.isPreface eq false)
                    val followingCondition = UserFollows.follower eq userId

                    val customAccessSubquery = CustomGroupUsers
                        .slice(CustomGroupUsers.accessGroup)
                        .select { CustomGroupUsers.member eq userId }

                    val accessCondition = (AccessGroups.type eq AccessGroupType.EVERYONE) or
                            (AccessGroups.type eq AccessGroupType.REGISTERED_USERS) or
                            (AccessGroups.type eq AccessGroupType.CUSTOM and Posts.readGroup.inSubQuery(customAccessSubquery))

                    baseCondition and followingCondition and accessCondition
                }
                .orderBy(Posts.creationTime to pageable.direction)
                .groupBy(Posts.id)

            val totalCount = query.count()
            val totalPages = ceil(totalCount / pageable.size.toDouble()).toInt()
            val offset = (pageable.page - 1) * pageable.size

            val results = query
                .limit(pageable.size, offset.toLong())
                .map { row -> toPostView(Viewer.Registered(userId), row) }
            Page(results, pageable.page, totalPages)
        }
    }

    override fun updatePost(userId: UUID, post: PostDto.Update): PostDto.View {
        return transaction {
            val postEntity = post.id.let { PostEntity.findById(it) } ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId) throw WrongUserException()

            val newUri = if ((post.uri.isNotEmpty() && post.uri != postEntity.uri) || (post.title != postEntity.title)) {
                checkOrCreateUri(userId, post.title, post.uri)
            } else {
                post.uri
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

    override fun deletePost(userId: UUID, postId: UUID): Unit {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: return@transaction
            if (postEntity.authorId.value != userId) throw WrongUserException()
            deletePost(postEntity)
        }
    }

    override fun addComment(userId: UUID, comment: CommentDto.Create): CommentDto.View {
        return transaction {
            val postEntity = PostEntity.findById(comment.postId) ?: throw PostNotFoundException()
            val viewer = Viewer.Registered(userId)
            if (userId != postEntity.authorId.value && (!accessGroupService.inGroup(viewer, postEntity.readGroupId.value) || !accessGroupService.inGroup(viewer, postEntity.commentGroupId.value))) {
                throw WrongUserException()
            }
            if (comment.parentCommentId != null) {
                val parentComment = CommentEntity.findById(comment.parentCommentId) ?: throw InvalidParentComment()
                if (parentComment.postId.value != comment.postId) throw InvalidParentComment()
            }
            val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()
            val commentId = Comments.insertAndGetId {
                it[post] = postEntity.id
                it[author] = userId
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

            CommentEntity.findById(commentId)!!.toComment(this)
        }
    }

    override fun updateComment(userId: UUID, comment: CommentDto.Update): CommentDto.View {
        return transaction {
            val commentEntity = CommentEntity.findById(comment.id) ?: throw CommentNotFoundException()
            if (userId != commentEntity.authorId.value) throw WrongUserException()
            commentEntity.apply {
                avatar = comment.avatar
                text = comment.text
            }
            commentEntity.toComment(this)
        }
    }

    override fun deleteComment(userId: UUID, commentId: UUID): Unit {
        return transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw CommentNotFoundException()
            if (userId != commentEntity.authorId.value) throw WrongUserException()

            val postId = commentEntity.postId
            commentEntity.delete()

            // Find the most recent remaining comment for this post
            val latestComment = CommentEntity
                .find { Comments.post eq postId }
                .orderBy(Comments.creationTime to SortOrder.DESC)
                .firstOrNull()

            // Update post's lastCommentTime
            Posts.update({ Posts.id eq postId }) {
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
            val postUri = checkOrCreateUri(userId, post.title, post.uri)
            val diaryId = DiaryEntity.find { Diaries.owner eq userId }.first().id.value

            val (readGroupEntity, commentGroupEntity, reactionGroupEntity) = getReadAndCommentGroups(
                diaryId,
                post.readGroupId,
                post.commentGroupId,
                post.reactionGroupId
            )

            val commentReactionGroupEntity = validateCommentReactionGroup(diaryId, post.commentReactionGroupId)

            val postId = Posts.insertAndGetId {
                it[uri] = postUri

                it[diary] = diaryId
                it[author] = userId

                it[avatar] = post.avatar
                it[title] = post.title
                it[text] = post.text

                it[isPreface] = post.isPreface
                it[isEncrypted] = post.isEncrypted

                it[classes] = post.classes

                it[isArchived] = false

                it[readGroup] = readGroupEntity.id
                it[commentGroup] = commentGroupEntity.id
                it[reactionGroup] = reactionGroupEntity.id
                it[commentReactionGroup] = commentReactionGroupEntity.id
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

    private fun Transaction.checkOrCreateUri(authorId: UUID, postTitle: String, postUri: String): String {
        return if (postUri.isBlank()) {
            createUri(authorId, postTitle)
        } else {
            if (!isValidUri(postUri)) throw InvalidUriException()
            if (isUriBusy(authorId, postUri)) throw UriIsBusyException()
            postUri
        }
    }

    private fun Transaction.createUri(authorId: UUID, postTitle: String): String {
        val wordsPart = postTitle.lowercase().map { transliterate(it) }.joinToString("")
            .replace(Regex("[^-a-zA-Z0-9 ]"), "")
            .lowercase(Locale.getDefault())
            .split(Regex("\\s+"))
            .joinToString("-")
        if (wordsPart.isBlank()) {
            return UUID.randomUUID().toString()
        }

        if (!isUriBusy(authorId, wordsPart)) {
            return wordsPart
        }

        var length = 3
        while (true) {
            val prefix = generateRandomAlphanumericString(length)
            val uri = "$prefix-$wordsPart"
            if (!isUriBusy(authorId, uri)) {
                return uri
            }
            length++
        }
    }

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.isUriBusy(authorId: UUID, uri: String): Boolean {
        return PostEntity.find { (Posts.author eq authorId) and (Posts.uri eq uri) }.firstOrNull() != null
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

    private fun Transaction.toPostView(viewer: Viewer, postEntity: PostEntity): PostDto.View {
        val userId = (viewer as? Viewer.Registered)?.userId
        val author = UserEntity.findById(postEntity.authorId) ?: throw InternalServerError()
        val authorDiary = DiaryEntity.find { Diaries.owner eq author.id }.single()
        val isCommentable = (userId == postEntity.authorId.value) || (accessGroupService.inGroup(viewer, postEntity.commentGroupId.value))
        val isReactable = (userId == postEntity.authorId.value) || (accessGroupService.inGroup(viewer, postEntity.reactionGroupId.value))

        return PostDto.View(
            id = postEntity.id.value,
            uri = postEntity.uri,
            authorLogin = authorDiary.login,
            authorNickname = author.nickname,
            avatar = postEntity.avatar,
            title = postEntity.title,
            text = postEntity.text,
            creationTime = postEntity.creationTime,
            isEncrypted = postEntity.isEncrypted,
            isPreface = postEntity.isPreface,
            classes = postEntity.classes,
            tags = postEntity.tags.map { it.name }.toSet(),
            isCommentable = isCommentable,
            comments = getCommentsForPost(postEntity.id.value),
            readGroupId = postEntity.readGroupId.value,
            commentGroupId = postEntity.commentGroupId.value,
            reactionGroupId = postEntity.reactionGroupId.value,
            commentReactionGroupId = postEntity.reactionGroupId.value, // Using post's reaction group as default for comments
            isReactable = isReactable,
            reactions = collectReactionInfo(postEntity.id.value),
        )
    }

    private fun Transaction.toPostView(viewer: Viewer, row: ResultRow): PostDto.View {
        val userId = (viewer as? Viewer.Registered)?.userId
        return PostDto.View(
            id = row[Posts.id].value,
            uri = row[Posts.uri],

            avatar = row[Posts.avatar],
            authorNickname = row[Users.nickname],
            authorLogin = row[Diaries.login],

            title = row[Posts.title],
            text = row[Posts.text],
            creationTime = row[Posts.creationTime],

            isPreface = row[Posts.isPreface],
            isEncrypted = row[Posts.isEncrypted],

            tags = getTagsForPost(row[Posts.id].value),

            classes = row[Posts.classes],
            isCommentable = (userId == row[Users.id].value) || (accessGroupService.inGroup(viewer, row[Posts.commentGroup].value)),
            comments = getCommentsForPost(row[Posts.id].value),

            readGroupId = row[Posts.readGroup].value,
            commentGroupId = row[Posts.commentGroup].value,
            reactionGroupId = row[Posts.reactionGroup].value,
            commentReactionGroupId = row[Posts.reactionGroup].value, // Using post's reaction group as default for comments
            isReactable = (userId == row[Users.id].value) || (accessGroupService.inGroup(viewer, row[Posts.reactionGroup].value)),
            reactions = collectReactionInfo(row[Posts.id].value),
        )
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

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.getTagsForPost(postId: UUID): Set<String> {
        return PostTags
            .innerJoin(Tags)
            .slice(Tags.name)
            .select { PostTags.post eq postId }
            .map { it[Tags.name] }
            .toSet()
    }

    private fun Transaction.getCommentsForPost(postId: UUID): List<CommentDto.View> {
        return CommentEntity.find { Comments.post eq postId }.orderBy(Comments.creationTime to SortOrder.ASC).map { it.toComment(this) }
    }

    private fun Transaction.collectReactionInfo(postId: UUID): List<ReactionDto.ReactionInfo> {
        // Get reactions with their files
        val reactionData = (PostReactions innerJoin Reactions innerJoin Files)
            .slice(Reactions.id, Reactions.name, Files.id)
            .select { PostReactions.post eq postId }
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
            val userLogins = (PostReactions innerJoin Users innerJoin Diaries)
                .slice(Diaries.login)
                .select { (PostReactions.post eq postId) and (PostReactions.reaction eq reactionId) }
                .map { it[Diaries.login] }
                .distinct()
            reactionUsers[reactionId] = userLogins.toMutableList()
        }

        // Get anonymous reactions count
        val anonymousCounts = mutableMapOf<UUID, Int>()
        reactionData.forEach { (reactionId, _, _) ->
            val count = AnonymousPostReactions
                .select { (AnonymousPostReactions.post eq postId) and (AnonymousPostReactions.reaction eq reactionId) }
                .count()
            anonymousCounts[reactionId] = count.toInt()
        }

        // Create ReactionInfo objects
        return reactionData.map { (reactionId, name, fileId) ->
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


    @Suppress("UNUSED_PARAMETER")
    private fun CommentEntity.toComment(transaction: Transaction): CommentDto.View {
        val author = UserEntity.findById(authorId) ?: throw InternalServerError()
        val authorDiary = DiaryEntity.find { Diaries.owner eq authorId }.single()
        val viewer = Viewer.Registered(authorId.value)
        val isReactable = (authorId.value == author.id.value) || accessGroupService.inGroup(viewer, reactionGroupId.value)
        return CommentDto.View(
            id = id.value,
            authorLogin = authorDiary.login,
            authorNickname = author.nickname,
            avatar = avatar,
            text = text,
            creationTime = creationTime,
            isReactable = isReactable,
            reactions = reactionService.getCommentReactions(id.value),
            reactionGroupId = reactionGroupId.value,
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
}
