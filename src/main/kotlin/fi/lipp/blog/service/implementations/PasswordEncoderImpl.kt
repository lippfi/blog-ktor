package fi.lipp.blog.service.implementations

import at.favre.lib.crypto.bcrypt.BCrypt
import fi.lipp.blog.service.PasswordEncoder

class PasswordEncoderImpl(private val cost: Int): PasswordEncoder {
    override fun encode(password: String): String {
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray())
    }

    override fun matches(password: String, encoded: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), encoded.toCharArray())
        return result.verified
    }
}
