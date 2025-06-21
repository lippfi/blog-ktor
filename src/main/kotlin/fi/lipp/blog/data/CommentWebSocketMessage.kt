package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.util.UUID

@Serializable
sealed interface CommentWebSocketMessage {
    @Serializable
    @SerialName("CommentAdded")
    data class CommentAdded(
        val comment: CommentDto.View,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("CommentUpdated")
    data class CommentUpdated(
        val comment: CommentDto.View,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("CommentDeleted")
    data class CommentDeleted(
        @Serializable(with = UUIDSerializer::class)
        val commentId: UUID,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("ReactionAdded")
    data class ReactionAdded(
        @Serializable(with = UUIDSerializer::class)
        val commentId: UUID,
        val reaction: ReactionDto.ReactionInfo,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("ReactionRemoved")
    data class ReactionRemoved(
        @Serializable(with = UUIDSerializer::class)
        val commentId: UUID,
        val reaction: ReactionDto.ReactionInfo,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("Subscribe")
    data class Subscribe(
        @Serializable(with = UUIDSerializer::class)
        val postId: UUID,
    ) : CommentWebSocketMessage

    @Serializable
    @SerialName("Error")
    data class Error(
        val message: String,
    ) : CommentWebSocketMessage
}

val webSocketJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphic(CommentWebSocketMessage::class) {
            subclass(CommentWebSocketMessage.CommentAdded::class, CommentWebSocketMessage.CommentAdded.serializer())
            subclass(CommentWebSocketMessage.CommentUpdated::class, CommentWebSocketMessage.CommentUpdated.serializer())
            subclass(CommentWebSocketMessage.CommentDeleted::class, CommentWebSocketMessage.CommentDeleted.serializer())
            subclass(CommentWebSocketMessage.ReactionAdded::class, CommentWebSocketMessage.ReactionAdded.serializer())
            subclass(CommentWebSocketMessage.ReactionRemoved::class, CommentWebSocketMessage.ReactionRemoved.serializer())
            subclass(CommentWebSocketMessage.Subscribe::class, CommentWebSocketMessage.Subscribe.serializer())
            subclass(CommentWebSocketMessage.Error::class, CommentWebSocketMessage.Error.serializer())
        }
    }
}
