package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly
import java.util.UUID

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
        val timezone: String,
        val language: Language,
    ): UserDto

    @TestOnly
    data class FullProfileInfo(
        val id: UUID,
        val login: String,
        val email: String,
        val nickname: String,
        val registrationTime: LocalDateTime,
        val password: String,
    ) : UserDto

    @Serializable
    data class AdditionalInfo(
        val sex: Sex,
        val timezone: String,
        val language: Language,
        val nsfw: NSFWPolicy,
        val birthDate: LocalDate?,
    ): UserDto

    @Serializable
    data class DiaryInfo(
        val name: String,
        val subtitle: String,
        @Serializable(with = UUIDSerializer::class)
        val defaultReadGroup: UUID,
        @Serializable(with = UUIDSerializer::class)
        val defaultCommentGroup: UUID,
        @Serializable(with = UUIDSerializer::class)
        val defaultReactGroup: UUID,
    )

    @Serializable
    data class View(
        val login: String,
        val nickname: String,
        val avatarUri: String?,
    ) : UserDto
}

enum class Sex {
    MALE,
    FEMALE,
    UNDEFINED,
}

enum class NSFWPolicy {
    SHOW,
    HIDE,
    WARN,
}

enum class Language {
    RU,
    EN,
    KK,
    KK_CYRILLIC,
}
