package fi.lipp.blog.data

import java.util.*

data class BlogFile(val id: UUID, val ownerId: UUID, val extension: String, val type: FileType) {
    val name = id.toString() + extension
}