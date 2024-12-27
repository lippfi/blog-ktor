package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

sealed interface UserDto {
    @Serializable
    data class ProfileInfo(
        val login: String,
        val email: String,
        val nickname: String,
        val registrationTime: LocalDateTime,
    ) : UserDto
    
    @Serializable
    data class Login(
        val login: String,
        val password: String,
    ) : UserDto

    @Serializable
    data class Registration(
        val login: String,
        val email: String,
        val password: String,
        val nickname: String,
    ): UserDto
}
