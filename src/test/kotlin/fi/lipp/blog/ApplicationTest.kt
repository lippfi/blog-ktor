package fi.lipp.blog

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.AfterClass
import org.koin.core.context.GlobalContext.stopKoin
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        environment {
            config = MapApplicationConfig(
                "database.url" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                "database.driver" to "org.h2.Driver",
                "database.user" to "root",
                "database.password" to "",
                "mail.api_key" to "test-key",
                "mail.sender_email" to "test@resend.dev",
                "storage.base_dir" to "/tmp",
                "storage.base_url" to "http://localhost:8000",
                "jwt.secret" to "test-secret",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
                "geoip.database" to ""
            )
        }
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Hello World!", bodyAsText())
        }
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun tearDown() {
            stopKoin()
        }
    }
}
