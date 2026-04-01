package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.AccessGroupType
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.repository.ExternalUsers
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import java.util.UUID
import kotlin.math.ceil

internal class PostQueryHelper {
    val postDiary = Diaries.alias("post_diary")
    val localPostAuthor = Users.alias("local_post_author")
    val externalPostAuthor = ExternalUsers.alias("external_post_author")
    val externalUserLinkedUser = Users.alias("external_user_linked_user")
    val externalLinkedAuthorDiary = Diaries.alias("external_linked_author_diary")
    val localAuthorDiary = Diaries.alias("author_diary")

    data class PostSearchParams(
        val viewer: Viewer,
        val authorLogin: String? = null,
        val diaryLogin: String? = null,
        val text: String? = null,
        val tags: Pair<TagPolicy, Set<String>>? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val isHidden: Boolean? = null,
        val isFeed: Boolean = false,
    )

    data class PostRowsPage(
        val rows: List<ResultRow>,
        val currentPage: Int,
        val totalPages: Int,
    )

    val postBaseSlice: List<Expression<*>> = listOf(
        *Posts.columns.toTypedArray(),
        postDiary[Diaries.login],
        postDiary[Diaries.owner],

        localPostAuthor[Users.id],
        localPostAuthor[Users.nickname],
        localPostAuthor[Users.signature],

        externalPostAuthor[ExternalUsers.id],
        externalPostAuthor[ExternalUsers.user],
        externalPostAuthor[ExternalUsers.nickname],

        externalUserLinkedUser[Users.id],
        externalUserLinkedUser[Users.nickname],
        externalUserLinkedUser[Users.signature],

        externalLinkedAuthorDiary[Diaries.login],
        localAuthorDiary[Diaries.login],
    )

    fun ResultRow.postAuthorUserId(): UUID? {
        return when (this[Posts.authorType]) {
            PostAuthorType.LOCAL -> this[localPostAuthor[Users.id]].value
            PostAuthorType.EXTERNAL -> this[externalPostAuthor[ExternalUsers.user]]?.value
        }
    }

    fun getBasicPostJoin(): Join {
        return Posts
            .innerJoin(postDiary, { Posts.diary }, { postDiary[Diaries.id] })
            .leftJoin(localPostAuthor, { Posts.localAuthor }, { localPostAuthor[Users.id] })
            .leftJoin(externalPostAuthor, { Posts.externalAuthor }, { externalPostAuthor[ExternalUsers.id] })
            .leftJoin(externalUserLinkedUser, { externalPostAuthor[ExternalUsers.user] }, { externalUserLinkedUser[Users.id] })
            .leftJoin(externalLinkedAuthorDiary, { externalUserLinkedUser[Users.id] }, { externalLinkedAuthorDiary[Diaries.owner] })
            .innerJoin(AccessGroups, { Posts.readGroup }, { AccessGroups.id })
            .leftJoin(localAuthorDiary, { localPostAuthor[Users.id] }, { localAuthorDiary[Diaries.owner] })
    }

    fun authorMatchesUsers(userIds: List<UUID>): Op<Boolean> {
        return ((Posts.authorType eq PostAuthorType.LOCAL) and (localPostAuthor[Users.id] inList userIds)) or
                ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] inList userIds))
    }

    fun buildReadAccessCondition(viewer: Viewer): Op<Boolean> {
        return when (viewer) {
            is Viewer.Anonymous -> {
                AccessGroups.type eq AccessGroupType.EVERYONE
            }
            is Viewer.Registered -> {
                val uid = viewer.userId

                val isAuthor =
                    ((Posts.authorType eq PostAuthorType.LOCAL) and (Posts.localAuthor eq uid)) or
                            ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] eq uid))

                val customAccessSubquery = CustomGroupUsers
                    .slice(CustomGroupUsers.accessGroup)
                    .select { CustomGroupUsers.member eq uid }

                val friendsUsers = Friends
                    .slice(Friends.user2)
                    .select { Friends.user1 eq uid }
                    .union(
                        Friends
                            .slice(Friends.user1)
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

    private fun ignoredAuthorCondition(viewerUserId: UUID): Op<Boolean> {
        val ignoredUsersSubquery = IgnoreList
            .slice(IgnoreList.ignoredUser)
            .select { IgnoreList.user eq viewerUserId }

        return ((Posts.authorType eq PostAuthorType.LOCAL) and (Posts.localAuthor inSubQuery ignoredUsersSubquery)) or
            ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] inSubQuery ignoredUsersSubquery))
    }

    private fun authorIgnoredMeCondition(viewerUserId: UUID): Op<Boolean> {
        val usersWhoIgnoredMeSubquery = IgnoreList
            .slice(IgnoreList.user)
            .select { IgnoreList.ignoredUser eq viewerUserId }

        return ((Posts.authorType eq PostAuthorType.LOCAL) and (Posts.localAuthor inSubQuery usersWhoIgnoredMeSubquery)) or
            ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] inSubQuery usersWhoIgnoredMeSubquery))
    }

    private fun hiddenFromFeedAuthorCondition(viewerUserId: UUID): Op<Boolean> {
        val hiddenUsersSubquery = HiddenFromFeed
            .slice(HiddenFromFeed.hiddenUser)
            .select { HiddenFromFeed.user eq viewerUserId }

        return ((Posts.authorType eq PostAuthorType.LOCAL) and (Posts.localAuthor inSubQuery hiddenUsersSubquery)) or
            ((Posts.authorType eq PostAuthorType.EXTERNAL) and (externalPostAuthor[ExternalUsers.user] inSubQuery hiddenUsersSubquery))
    }

    private fun visibleToViewerCondition(viewerUserId: UUID): Op<Boolean> {
        return not(ignoredAuthorCondition(viewerUserId)) and not(authorIgnoredMeCondition(viewerUserId))
    }

    fun Query.andFilters(
        text: String?,
        authorLogin: String?,
        diaryLogin: String?,
        from: LocalDate?,
        to: LocalDate?,
        isHidden: Boolean?,
        isFeed: Boolean,
        viewer: Viewer,
    ): Query {
        return this.apply {
            andWhere {
                var cond: Op<Boolean> = Posts.isArchived eq false

                if (text != null) {
                    cond = cond and (
                            Posts.text.regexp(stringParam(text), false) or
                                    Posts.title.regexp(stringParam(text), false)
                            )
                }

                if (authorLogin != null) {
                    cond = cond and (
                        (localAuthorDiary[Diaries.login] eq authorLogin) or
                        (externalLinkedAuthorDiary[Diaries.login] eq authorLogin)
                    )
                }

                if (diaryLogin != null) {
                    cond = cond and (postDiary[Diaries.login] eq diaryLogin)
                }

                if (from != null) {
                    cond = cond and (Posts.creationTime greaterEq from.atTime(0, 0))
                }

                if (to != null) {
                    cond = cond and (Posts.creationTime lessEq to.atTime(23, 59, 59))
                }

                if (isHidden != null) {
                    cond = cond and (Posts.isHidden eq isHidden)
                }

                if (isFeed && viewer is Viewer.Registered) {
                    cond = cond and not(hiddenFromFeedAuthorCondition(viewer.userId))
                }

                if (viewer is Viewer.Registered) {
                    cond = cond and visibleToViewerCondition(viewer.userId)
                }

                cond
            }
        }
    }

    fun Query.andTagFilter(tags: Pair<TagPolicy, Set<String>>?): Query {
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

    fun buildPostQuery(params: PostSearchParams): Query {
        val baseJoin = getBasicPostJoin()
        val query = baseJoin
            .slice(postBaseSlice)
            .select { buildReadAccessCondition(params.viewer) }

        return query
            .andFilters(
                text = params.text,
                authorLogin = params.authorLogin,
                diaryLogin = params.diaryLogin,
                from = params.from,
                to = params.to,
                isHidden = params.isHidden,
                isFeed = params.isFeed,
                viewer = params.viewer,
            )
            .andTagFilter(params.tags)
    }

    fun executePaged(
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

        return PostRowsPage(
            rows = rows,
            currentPage = pageable.page,
            totalPages = totalPages,
        )
    }

    fun loadSinglePostRow(where: SqlExpressionBuilder.() -> Op<Boolean>): ResultRow? {
        return getBasicPostJoin()
            .slice(postBaseSlice)
            .select { where(SqlExpressionBuilder) }
            .limit(1)
            .firstOrNull()
    }

    fun loadVisibleSinglePostRow(
        viewer: Viewer,
        where: SqlExpressionBuilder.() -> Op<Boolean>
    ): ResultRow? {
        val query = getBasicPostJoin()
            .slice(postBaseSlice)
            .select {
                buildReadAccessCondition(viewer) and where(SqlExpressionBuilder)
            }
            .andFilters(
                text = null,
                authorLogin = null,
                diaryLogin = null,
                from = null,
                to = null,
                isHidden = null,
                isFeed = false,
                viewer = viewer,
            )

        return query
            .limit(1)
            .firstOrNull()
    }

    fun loadVisibleSinglePostRowOrThrow(
        viewer: Viewer,
        where: SqlExpressionBuilder.() -> Op<Boolean>
    ): ResultRow {
        return loadVisibleSinglePostRow(viewer, where) ?: throw PostNotFoundException()
    }
}
