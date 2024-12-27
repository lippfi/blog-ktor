package fi.lipp.blog.model

import org.jetbrains.exposed.sql.SortOrder

data class Pageable(
    val page: Int,
    val size: Int,
    val direction: SortOrder
)