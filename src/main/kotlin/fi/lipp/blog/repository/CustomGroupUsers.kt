package fi.lipp.blog.repository

import org.jetbrains.exposed.sql.Table

object CustomGroupUsers : Table() {
    val accessGroup = reference("group", AccessGroups)
    val member = reference("member", Users)

    override val primaryKey = PrimaryKey(accessGroup, member)
}