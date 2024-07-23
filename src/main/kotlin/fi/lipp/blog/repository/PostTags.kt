package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.Table

object PostTags : Table() {
    val tag = reference("tag", Tags)
    val post = reference("post", Posts)
    override val primaryKey = PrimaryKey(post, tag)
}
