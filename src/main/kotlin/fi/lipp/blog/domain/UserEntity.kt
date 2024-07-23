package fi.lipp.blog.domain

import fi.lipp.blog.data.User
import fi.lipp.blog.repository.UserAvatars
import fi.lipp.blog.repository.Users
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.select

class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(Users)

    var login: String by Users.login
    var email: String by Users.email
    var password: String by Users.password
    var nickname: String by Users.nickname
    var registrationTime: LocalDateTime by Users.registrationTime
    val avatars: List<FileEntity>
        get() = UserAvatars
            .select { UserAvatars.user eq id }
            .orderBy(UserAvatars.ordinal)
            .mapNotNull { FileEntity.findById(it[UserAvatars.avatar].value) }

    fun toUser(): User {
        return User(id.value, login, email, password, nickname, registrationTime)
    }
}