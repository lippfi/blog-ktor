package fi.lipp.blog.data

import java.util.*

data class BlogFile(val id: UUID, val ownerId: UUID, val name: String, val type: FileType)