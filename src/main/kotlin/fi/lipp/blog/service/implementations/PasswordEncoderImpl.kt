package fi.lipp.blog.service.implementations

import at.favre.lib.crypto.bcrypt.BCrypt
import fi.lipp.blog.service.PasswordEncoder

class PasswordEncoderImpl: PasswordEncoder {
    override fun encode(password: String): String {
        return BCrypt.withDefaults().hashToString(15, password.toCharArray())
    }

    override fun matches(password: String, encoded: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), encoded.toCharArray())
        return result.verified
    }
}