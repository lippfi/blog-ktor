package fi.lipp.blog.stubs

import fi.lipp.blog.service.PasswordEncoder

class PasswordEncoderStub: PasswordEncoder {
    override fun encode(password: String): String {
        return password.reversed()
    }

    override fun matches(password: String, encoded: String): Boolean {
        return encoded.reversed() == password
    }
}