package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object PostTags : Table() {
    val tag = reference("tag", Tags, onDelete = ReferenceOption.CASCADE)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(post, tag)
}
