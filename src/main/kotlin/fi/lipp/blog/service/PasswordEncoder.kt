package fi.lipp.blog.service

interface PasswordEncoder {
    fun encode(password: String): String
    fun matches(password: String, encoded: String): Boolean
}