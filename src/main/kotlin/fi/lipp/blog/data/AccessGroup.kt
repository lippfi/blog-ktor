package fi.lipp.blog.data

data class AccessGroup(
    val diaryId: Long?,
    val name: String,
    val type: AccessGroupType,
) {

}