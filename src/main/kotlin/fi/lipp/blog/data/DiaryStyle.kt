package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DiaryStyle(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val enabled: Boolean,
    val styleContent: String,
    val previewPictureUri: String?
)

@Serializable
data class DiaryStyleCreate(
    val name: String,
    val styleContent: String,
    val previewPictureUri: String?,
    val enabled: Boolean,
)

@Serializable
data class DiaryStyleUpdate(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val styleContent: String,
    val enabled: Boolean,
    val previewPictureUri: String,
)
