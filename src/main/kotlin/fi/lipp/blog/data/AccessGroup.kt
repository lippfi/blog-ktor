package fi.lipp.blog.data

import java.util.UUID

data class AccessGroup(
    val diaryId: UUID?,
    val name: String,
    val type: AccessGroupType,
) {

}