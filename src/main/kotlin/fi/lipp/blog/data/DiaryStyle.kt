package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DiaryStylePreview(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
)

@Serializable
data class DiaryStyle(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val styleUri: String,
    val styleContent: String,
    val authorLogin: String,
    val authorNickname: String
)

@Serializable
data class DiaryStyleCreate(
    val name: String,
    val description: String?,
    val styleContent: String,
    val enabled: Boolean,
)

@Serializable
data class DiaryStyleUpdate(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val description: String?,
    val styleContent: String,
    val enabled: Boolean,
)
