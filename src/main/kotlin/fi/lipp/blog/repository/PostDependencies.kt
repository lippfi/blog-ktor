package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object PostDependencies : UUIDTable() {
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)

    init {
        uniqueIndex("post_dependencies_unique", post, user)
    }
}
