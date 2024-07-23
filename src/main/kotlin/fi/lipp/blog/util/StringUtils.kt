package fi.lipp.blog.util

fun String.lastIndexOfOrNull(char: Char, startIndex: Int = 0, ignoreCase: Boolean = false): Int? {
    val index = indexOf(char, startIndex, ignoreCase)
    if (index < 0) return null
    return index
}