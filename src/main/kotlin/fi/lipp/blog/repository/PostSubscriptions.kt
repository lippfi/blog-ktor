package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object PostSubscriptions : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val post = reference("post", Posts, onDelete = ReferenceOption.CASCADE)
    
    init {
        uniqueIndex("idx_post_subscription", user, post)
    }
}