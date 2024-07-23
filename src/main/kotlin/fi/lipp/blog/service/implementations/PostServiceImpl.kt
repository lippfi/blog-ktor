package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.Comment
import fi.lipp.blog.model.Page
import fi.lipp.blog.data.Post
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.domain.TagEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
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

class PostServiceImpl : PostService {
    override fun getPreface(userId: Long, diaryId: Long): Post? {
        return transaction {
            val preface = PostEntity.find { (Posts.diary eq diaryId) and (Posts.isPreface eq true) and (Posts.isArchived eq false) }.firstOrNull() ?: return@transaction null
            if (!preface.isPrivate || (preface.isPrivate && preface.authorId.value == userId)) {
                preface.toPost()
            } else {
                null
            }
        }
    }

    override fun getPost(userId: Long, authorLogin: String, uri: String): Post? {
        return transaction {
            val userEntity = UserEntity.find { Users.login eq authorLogin }.firstOrNull() ?: throw UserNotFoundException()
            val postEntity = PostEntity.find { (Posts.author eq userEntity.id) and (Posts.uri eq uri) and (Posts.isArchived eq false) }.firstOrNull() ?: return@transaction null
            if ((postEntity.authorId.value == userId) || !postEntity.isPrivate) {
                postEntity.toPost()
            } else {
                null
            }
        }
    }

    override fun getPosts(
        userId: Long,
        authorId: Long?,
        diaryId: Long?,
        text: String?,
        tags: Pair<TagPolicy, Set<String>>?,
        from: LocalDateTime?,
        to: LocalDateTime?,
        pageable: Pageable
    ): Page<Post> {
        return transaction {
            val query = Posts
                .innerJoin(Diaries)
                .innerJoin(Users, { Posts.author }, { Users.id })
                .leftJoin(PostTags)
                .leftJoin(Tags)
                .slice(Posts.columns + Users.nickname + Users.login)
                .select {
                    (Posts.isArchived eq false) and (Posts.isPreface eq false) and ((Posts.author eq userId) or (Posts.isPrivate eq false))
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
                .map { row ->
                    Post(
                        id = row[Posts.id].value,
                        uri = row[Posts.uri],

                        avatar = row[Posts.avatar],
                        authorNickname = row[Users.nickname],
                        authorLogin = row[Users.login],

                        title = row[Posts.title],
                        text = row[Posts.text],
                        creationTime = row[Posts.creationTime],

                        isPreface = row[Posts.isPreface],
                        isPrivate = row[Posts.isPrivate],
                        isEncrypted = row[Posts.isEncrypted],

                        tags = getTagsForPost(row[Posts.id].value),

                        classes = row[Posts.classes],
                    )
                }

            Page(results, pageable.page, totalPages)
        }
    }

    private fun getTagsForPost(postId: UUID): Set<String> {
        return PostTags
            .innerJoin(Tags)
            .slice(Tags.name)
            .select { PostTags.post eq postId }
            .map { it[Tags.name] }
            .toSet()
    }


    override fun addPost(userId: Long, post: Post) {
        transaction {
            if (post.isPreface) {
                addPreface(userId, post)
            } else {
                addPostToDb(userId, post)
            }
        }
    }

    private fun addPreface(userId: Long, post: Post) {
        val preface = PostEntity.find { (Posts.author eq userId) and (Posts.isPreface eq true) }.firstOrNull()
        if (preface != null) {
            deletePost(preface)
        }

        addPostToDb(userId, post)
    }

    override fun updatePost(userId: Long, post: Post) {
        transaction {
            val postEntity = post.id?.let { PostEntity.findById(it) } ?: throw PostNotFoundException()
            if (postEntity.authorId.value != userId) throw WrongUserException()

            val newUri = if ((post.uri.isNotEmpty() && post.uri != postEntity.uri) || (post.title != postEntity.title)) {
                checkOrCreateUri(userId, post)
            } else {
                post.uri
            }

            postEntity.apply {
                uri = newUri

                avatar = post.avatar
                title = post.title
                text = post.text

                classes = post.classes

                isPrivate = post.isPrivate
                isEncrypted = post.isEncrypted
            }
            updatePostTags(postEntity, post.tags)
        }
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

    override fun addComment(userId: Long, comment: Comment) {

        TODO("Not yet implemented")
    }

    override fun updateComment(userId: Long, comment: Comment) {
        TODO("Not yet implemented")
    }

    override fun deleteComment(userId: Long, commentId: UUID) {
        TODO("Not yet implemented")
    }

    private fun deletePost(postEntity: PostEntity) {
        postEntity.apply {
            isArchived = true
            uri = UUID.randomUUID().toString()
        }
    }

    private fun addPostToDb(userId: Long, post: Post) {
        transaction {
            val postUri = checkOrCreateUri(userId, post)
            val diaryId = DiaryEntity.find { Diaries.owner eq userId }.first().id

            val postId = Posts.insertAndGetId {
                it[uri] = postUri

                it[diary] = diaryId
                it[author] = userId

                it[avatar] = post.avatar
                it[title] = post.title
                it[text] = post.text

                it[isPreface] = post.isPreface
                it[isPrivate] = post.isPrivate
                it[isEncrypted] = post.isEncrypted

                it[classes] = post.classes

                it[isArchived] = false
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

    private fun checkOrCreateUri(authorId: Long, post: Post): String {
        return if (post.uri.isBlank()) {
            createUri(authorId, post)
        } else {
            if (!isValidUri(post.uri)) throw InvalidUriException()
            if (isUriBusy(authorId, post.uri)) throw UriIsBusyException()
            post.uri
        }
    }

    private fun createUri(authorId: Long, post: Post): String {
        val wordsPart = post.title
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