package fi.lipp.blog.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID
import fi.lipp.blog.data.FileUploadData

sealed interface ReactionDto {
    @Serializable
    data class Create(
        val name: String,
        @Contextual val icon: FileUploadData,
        val localizations: Map<Language, String>
    ) : ReactionDto

    @Serializable
    data class Update(
        @Contextual val id: UUID,
        val name: String,
        @Contextual val icon: FileUploadData,
        val localizations: Map<Language, String>
    ) : ReactionDto

    @Serializable
    data class View(
        @Contextual val id: UUID,
        val name: String,
        val iconUri: String,
        val localizations: Map<Language, String>
    ) : ReactionDto

    @Serializable
    data class AddLocalization(
        @Contextual val id: UUID,
        val language: Language,
        val localizedName: String
    ) : ReactionDto
}
