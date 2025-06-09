package fi.lipp.blog.model

import kotlinx.serialization.Serializable

@Serializable
data class Page<T>(
    var content: List<T>,
    var currentPage: Int,
    var totalPages: Int
)