package fi.lipp.blog.domain

import fi.lipp.blog.repository.UserAvatars
import fi.lipp.blog.repository.Users
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.select
import java.util.UUID

class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(Users)

    var login: String by Users.login
    var email: String by Users.email
    var password: String by Users.password
    var nickname: String by Users.nickname
    var registrationTime: LocalDateTime by Users.registrationTime
}