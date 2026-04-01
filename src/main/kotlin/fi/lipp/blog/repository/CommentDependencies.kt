package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object CommentDependencies : UUIDTable() {
    val comment = reference("comment", Comments, onDelete = ReferenceOption.CASCADE)
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex("comment_dependencies_unique", comment, user)
    }
}