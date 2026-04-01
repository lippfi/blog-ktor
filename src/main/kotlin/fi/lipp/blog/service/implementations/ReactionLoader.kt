package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import java.util.UUID

internal class ReactionLoader(private val storageService: StorageService) {

    private data class ReactionMeta(val name: String, val iconUri: String)

    private data class RegisteredReactionRow(val targetId: UUID, val reactionId: UUID, val user: ReactionDto.UserInfo)

    fun loadPostReactions(transaction: Transaction, viewer: Viewer, postIds: Set<UUID>): Map<UUID, List<ReactionDto.ReactionInfo>> {
        if (postIds.isEmpty()) return emptyMap()

        val userId = (viewer as? Viewer.Registered)?.userId
        val ignoreConditions = buildIgnoreConditions(userId) { PostReactions.user }

        val baseConditions: List<Op<Boolean>> = listOf(PostReactions.post inList postIds.toList())
        val allConditions = baseConditions + ignoreConditions

        val regRows =
            PostReactions
                .innerJoin(Reactions)
                .innerJoin(Users, { PostReactions.user }, { Users.id })
                .innerJoin(Diaries, { Users.id }, { Diaries.owner })
                .slice(PostReactions.post, Reactions.id, Diaries.login, Users.nickname)
                .select { allConditions.reduce { acc, condition -> acc and condition } }
                .map {
                    RegisteredReactionRow(
                        targetId = it[PostReactions.post].value,
                        reactionId = it[Reactions.id].value,
                        user = ReactionDto.UserInfo(
                            login = it[Diaries.login],
                            nickname = it[Users.nickname]
                        )
                    )
                }

        val anonymousCounts =
            AnonymousPostReactions
                .slice(
                    AnonymousPostReactions.post,
                    AnonymousPostReactions.reaction,
                    AnonymousPostReactions.reaction.count()
                )
                .select { AnonymousPostReactions.post inList postIds.toList() }
                .groupBy(AnonymousPostReactions.post, AnonymousPostReactions.reaction)
                .associate { row ->
                    (row[AnonymousPostReactions.post].value to row[AnonymousPostReactions.reaction].value) to
                            row[AnonymousPostReactions.reaction.count()].toInt()
                }

        val reactionIds =
            regRows.map { it.reactionId }.toSet() +
                    anonymousCounts.keys.map { it.second }.toSet()

        val reactionMeta = loadReactionMeta(transaction, reactionIds)
        return mergeReactions(postIds, regRows, anonymousCounts, reactionMeta)
    }

    fun loadCommentReactions(transaction: Transaction, commentIds: Set<UUID>, viewer: Viewer? = null): Map<UUID, List<ReactionDto.ReactionInfo>> {
        if (commentIds.isEmpty()) return emptyMap()

        val userId = (viewer as? Viewer.Registered)?.userId
        val ignoreConditions = buildIgnoreConditions(userId) { CommentReactions.user }

        val baseConditions: List<Op<Boolean>> = listOf(CommentReactions.comment inList commentIds.toList())
        val allConditions = baseConditions + ignoreConditions

        val regRows =
            CommentReactions
                .innerJoin(Reactions)
                .innerJoin(Users, { CommentReactions.user }, { Users.id })
                .innerJoin(Diaries, { Users.id }, { Diaries.owner })
                .slice(
                    CommentReactions.comment,
                    Reactions.id,
                    Diaries.login,
                    Users.nickname
                )
                .select { allConditions.reduce { acc, condition -> acc and condition } }
                .map {
                    RegisteredReactionRow(
                        targetId = it[CommentReactions.comment].value,
                        reactionId = it[Reactions.id].value,
                        user = ReactionDto.UserInfo(
                            login = it[Diaries.login],
                            nickname = it[Users.nickname]
                        )
                    )
                }

        val anonymousCounts =
            AnonymousCommentReactions
                .slice(
                    AnonymousCommentReactions.comment,
                    AnonymousCommentReactions.reaction,
                    AnonymousCommentReactions.reaction.count()
                )
                .select { AnonymousCommentReactions.comment inList commentIds.toList() }
                .groupBy(
                    AnonymousCommentReactions.comment,
                    AnonymousCommentReactions.reaction
                )
                .associate { row ->
                    (row[AnonymousCommentReactions.comment].value to row[AnonymousCommentReactions.reaction].value) to
                            row[AnonymousCommentReactions.reaction.count()].toInt()
                }

        val reactionIds =
            regRows.map { it.reactionId }.toSet() +
                    anonymousCounts.keys.map { it.second }.toSet()

        val reactionMeta = loadReactionMeta(transaction, reactionIds)
        return mergeReactions(commentIds, regRows, anonymousCounts, reactionMeta)
    }

    private fun loadReactionMeta(transaction: Transaction, reactionIds: Set<UUID>): Map<UUID, ReactionMeta> {
        if (reactionIds.isEmpty()) return emptyMap()

        data class RawReactionMeta(
            val reactionId: UUID,
            val reactionName: String,
            val iconFile: BlogFile
        )

        val rows = (Reactions innerJoin Files)
            .slice(
                Reactions.id,
                Reactions.name,
                Files.id,
                Files.name,
                Files.owner,
                Files.fileType
            )
            .select { Reactions.id inList reactionIds.toList() }
            .map { row ->
                RawReactionMeta(
                    reactionId = row[Reactions.id].value,
                    reactionName = row[Reactions.name],
                    iconFile = BlogFile(
                        id = row[Files.id].value,
                        ownerId = row[Files.owner].value,
                        name = row[Files.name],
                        type = row[Files.fileType]
                    )
                )
            }

        val iconUrls = storageService.getFileURLs(rows.map { it.iconFile })

        return rows.associate { row ->
            row.reactionId to ReactionMeta(
                name = row.reactionName,
                iconUri = iconUrls[row.iconFile.id] ?: throw InternalServerError()
            )
        }
    }

    private fun buildIgnoreConditions(userId: UUID?, userColumn: () -> Column<org.jetbrains.exposed.dao.id.EntityID<UUID>>): List<Op<Boolean>> {
        if (userId == null) return emptyList()

        val ignoredUsersSubquery = IgnoreList
            .slice(IgnoreList.ignoredUser)
            .select { IgnoreList.user eq userId }

        val usersWhoIgnoredMeSubquery = IgnoreList
            .slice(IgnoreList.user)
            .select { IgnoreList.ignoredUser eq userId }

        return listOf(
            userColumn() notInSubQuery ignoredUsersSubquery,
            userColumn() notInSubQuery usersWhoIgnoredMeSubquery
        )
    }

    private fun mergeReactions(
        targetIds: Set<UUID>,
        registeredRows: List<RegisteredReactionRow>,
        anonymousCounts: Map<Pair<UUID, UUID>, Int>,
        reactionMeta: Map<UUID, ReactionMeta>
    ): Map<UUID, List<ReactionDto.ReactionInfo>> {

        val usersByTargetReaction =
            registeredRows
                .groupBy { it.targetId }
                .mapValues { (_, rows) ->
                    rows.groupBy { it.reactionId }
                        .mapValues { (_, reactionRows) ->
                            reactionRows.map { it.user }
                        }
                }

        val result = mutableMapOf<UUID, MutableMap<UUID, ReactionDto.ReactionInfo>>()
        targetIds.forEach { result[it] = mutableMapOf() }

        usersByTargetReaction.forEach { (targetId, byReaction) ->
            val perTarget = result.getValue(targetId)

            byReaction.forEach { (reactionId, users) ->
                val meta = reactionMeta.getValue(reactionId)
                val anonymousCount = anonymousCounts[targetId to reactionId] ?: 0

                perTarget[reactionId] =
                    ReactionDto.ReactionInfo(
                        id = reactionId,
                        name = meta.name,
                        iconUri = meta.iconUri,
                        count = users.size + anonymousCount,
                        users = users,
                        anonymousCount = anonymousCount
                    )
            }
        }

        anonymousCounts.forEach { (key, anonymousCount) ->
            val (targetId, reactionId) = key
            val perTarget = result.getValue(targetId)

            if (perTarget[reactionId] != null) return@forEach

            val meta = reactionMeta.getValue(reactionId)

            perTarget[reactionId] =
                ReactionDto.ReactionInfo(
                    id = reactionId,
                    name = meta.name,
                    iconUri = meta.iconUri,
                    count = anonymousCount,
                    users = emptyList(),
                    anonymousCount = anonymousCount
                )
        }

        return result.mapValues { (_, perTarget) -> perTarget.values.toList() }
    }
}
