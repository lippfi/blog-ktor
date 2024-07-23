package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class User(
    val id: Long,
    val login: String,
    val email: String,
    @Transient
    val password: String = "",
    val nickname: String,
    val registrationTime: LocalDateTime
)