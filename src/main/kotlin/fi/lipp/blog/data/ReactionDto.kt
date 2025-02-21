package fi.lipp.blog.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.util.UUIDSerializer

sealed interface ReactionDto {
    companion object {
        private val namePattern = Regex("^[a-zA-Z][a-zA-Z0-9-]*$")

        fun validateName(name: String) {
            require(name.matches(namePattern)) { "Reaction name must start with a letter and contain only English letters, numbers, and hyphens" }
        }
    }

    @Serializable
    data class ReactionInfo(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        val iconUri: String,
        val count: Int,
        val userLogins: List<String>,
        val anonymousCount: Int
    ) : ReactionDto

    @Serializable
    data class View(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val name: String,
        val iconUri: String,
    ) : ReactionDto
}
