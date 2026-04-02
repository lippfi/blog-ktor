package fi.lipp.blog.repository

import fi.lipp.blog.data.UserPermission
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object UserPermissions : Table() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val permission = enumerationByName("permission", 100, UserPermission::class)

    override val primaryKey = PrimaryKey(user, permission)
}
