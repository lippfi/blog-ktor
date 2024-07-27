package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object CustomGroupUsers : Table() {
    val accessGroup = reference("group", AccessGroups, onDelete = ReferenceOption.CASCADE)
    val member = reference("member", Users, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(accessGroup, member)
}