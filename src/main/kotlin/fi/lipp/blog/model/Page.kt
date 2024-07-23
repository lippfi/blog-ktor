package fi.lipp.blog.model

data class Page<T>(
    var content: List<T>,
    var currentPage: Int,
    var totalPages: Int
)