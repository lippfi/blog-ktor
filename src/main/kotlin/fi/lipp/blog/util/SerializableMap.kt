package fi.lipp.blog.util

import kotlinx.serialization.Serializable

@Serializable
data class SerializableMap(
    val content: Map<String, String>
)