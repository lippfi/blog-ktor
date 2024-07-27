package fi.lipp.blog.mapper

import fi.lipp.blog.data.PostUpdateData
import fi.lipp.blog.data.PostView
import fi.lipp.blog.domain.PostEntity
import org.jetbrains.exposed.sql.ResultRow

interface PostMapper {
    fun toPostView(userId: Long?, postEntity: PostEntity): PostView
    fun toPostView(userId: Long?, row: ResultRow): PostView

    fun toPostUpdateData(postEntity: PostEntity): PostUpdateData
}