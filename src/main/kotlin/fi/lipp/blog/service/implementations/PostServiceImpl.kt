package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.domain.*
import fi.lipp.blog.mapper.PostMapper
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.PostService
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random

class PostServiceImpl(private val postMapper: PostMapper, private val accessGroupService: AccessGroupService) : PostService {
    override fun getPostForEdit(userId: Long, postId: UUID): PostUpdateData {
        return transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId) throw WrongUserException()
            return@transaction postMapper.toPostUpdateData(postEntity)
        }
    }

    override fun getPreface(userId: Long?, diaryId: Long): PostView? {
        return transaction {
            val preface = PostEntity.find { (Posts.diary eq diaryId) and (Posts.isPreface eq true) and (Posts.isArchived eq false) }.firstOrNull() ?: return@transaction null
            return@transaction if (userId == preface.authorId.value || accessGroupService.inGroup(userId, preface.readGroupId.value)) {
                postMapper.toPostView(userId, preface)
            } else {
                null
            }
        }
    }

    override fun getPost(userId: Long?, authorLogin: String, uri: String): PostView {
        return transaction {
            val userEntity = UserEntity.find { Users.login eq authorLogin }.firstOrNull() ?: throw UserNotFoundException()
            val postEntity = PostEntity.find { (Posts.author eq userEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: throw PostNotFoundException()
            return@transaction if (userId == postEntity.authorId.value || accessGroupService.inGroup(userId, postEntity.readGroupId.value)) {
                postMapper.toPostView(userId, postEntity)
            } else {
                throw PostNotFoundException()
            }
        }
    }

    override fun getPosts(
        userId: Long?,
        authorId: Long?,
        diaryId: Long?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable,
    ): Page<PostView> {
        return transaction {
            val query = Posts
                // TODO use it in case code below does not work
                .innerJoin(Diaries)
                .innerJoin(Users, { Posts.author }, { Users.id })
                .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
                .leftJoin(PostTags)
                .leftJoin(Tags)
                .slice(Posts.columns + Users.id + Users.nickname + Users.login + AccessGroups.type)
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
            val totalPages = ceil(totalCount / pageable.pageSize.toDouble()).toInt()
            val offset = (pageable.page - 1) * pageable.pageSize

            val results = query
                .limit(pageable.pageSize, offset.toLong())
                .map { row -> postMapper.toPostView(userId, row) }
            Page(results, pageable.page, totalPages)
        }
    }

    override fun addPost(userId: Long, post: PostPostData) {
        transaction {
            if (post.isPreface) {
                addPreface(userId, post)
            } else {
                addPostToDb(userId, post)
            }
        }
    }

    private fun addPreface(userId: Long, post: PostPostData) {
        val preface = PostEntity.find { (Posts.author eq userId) and (Posts.isPreface eq true) }.firstOrNull()
        if (preface != null) {
            deletePost(preface)
        }

        addPostToDb(userId, post)
    }

    override fun updatePost(userId: Long, post: PostUpdateData) {
        transaction {
            val postEntity = post.id.let { PostEntity.findById(it) } ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId) throw WrongUserException()

            val newUri = if ((post.uri.isNotEmpty() && post.uri != postEntity.uri) || (post.title != postEntity.title)) {
                checkOrCreateUri(userId, post.title, post.uri)
            } else {
                post.uri
            }

            val (readGroup, commentGroup) = getReadAndCommentGroups(postEntity.diaryId.value, post.readGroupId, post.commentGroupId)

            postEntity.apply {
                uri = newUri

                avatar = post.avatar
                title = post.title
                text = post.text

                classes = post.classes

                isEncrypted = post.isEncrypted
                readGroupId = readGroup.id
                commentGroupId = commentGroup.id
            }
            updatePostTags(postEntity, post.tags)
        }
    }

    private fun getReadAndCommentGroups(diaryId: Long, readGroupId: UUID, commentGroupId: UUID): Pair<AccessGroupEntity, AccessGroupEntity> {
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
        return readGroupEntity to commentGroupEntity
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

    override fun deletePost(userId: Long, postId: UUID) {
        transaction {
            val postEntity = PostEntity.findById(postId) ?: return@transaction
            if (postEntity.authorId.value != userId) throw WrongUserException()
            deletePost(postEntity)
        }
    }

    override fun addComment(userId: Long, comment: CommentPostData) {
        transaction {
            val postEntity = PostEntity.findById(comment.postId) ?: throw PostNotFoundException()
            if (userId != postEntity.authorId.value && (!accessGroupService.inGroup(userId, postEntity.readGroupId.value) || !accessGroupService.inGroup(userId, postEntity.commentGroupId.value))) {
                throw WrongUserException()
            }
            Comments.insert {
                it[post] = postEntity.id
                it[author] = userId
                it[avatar] = comment.avatar
                it[text] = comment.text
            }
        }
    }

    override fun updateComment(userId: Long, comment: CommentUpdateData) {
        transaction {
            val commentEntity = CommentEntity.findById(comment.id) ?: throw InvalidCommentException()
            if (userId != commentEntity.authorId.value) throw WrongUserException()
            commentEntity.apply {
                avatar = comment.avatar
                text = comment.text
            }
        }
    }

    override fun deleteComment(userId: Long, commentId: UUID) {
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: throw InvalidCommentException()
            if (userId != commentEntity.authorId.value) throw WrongUserException()
            commentEntity.delete()
        }
    }

    private fun deletePost(postEntity: PostEntity) {
        postEntity.apply {
            isArchived = true
            uri = UUID.randomUUID().toString()
        }
    }

    private fun addPostToDb(userId: Long, post: PostPostData) {
        transaction {
            val postUri = checkOrCreateUri(userId, post.title, post.uri)
            val diaryId = DiaryEntity.find { Diaries.owner eq userId }.first().id

            val (readGroupEntity, commentGroupEntity) = getReadAndCommentGroups(userId, post.readGroupId, post.commentGroupId)

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
            }

            for (tag in post.tags) {
                val tagId = getOrCreateTag(diaryId.value, tag)
                PostTags.insert {
                    it[PostTags.tag] = tagId
                    it[PostTags.post] = postId
                }
            }
        }
    }

    private fun getOrCreateTag(diaryId: Long, tag: String): EntityID<Long> {
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

    private fun checkOrCreateUri(authorId: Long, postTitle: String, postUri: String): String {
        return if (postUri.isBlank()) {
            createUri(authorId, postTitle)
        } else {
            if (!isValidUri(postUri)) throw InvalidUriException()
            if (isUriBusy(authorId, postUri)) throw UriIsBusyException()
            postUri
        }
    }

    private fun createUri(authorId: Long, postTitle: String): String {
        val wordsPart = postTitle
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
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
            val uri = prefix + "_" + wordsPart
            if (!isUriBusy(authorId, uri)) {
                return uri
            }
            length++
        }
    }

    private fun isUriBusy(authorId: Long, uri: String): Boolean {
        return PostEntity.find { (Posts.author eq authorId) and (Posts.uri eq uri) }.firstOrNull() != null
    }

    private fun generateRandomAlphanumericString(length: Int): String {
        val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }
}