package fi.lipp.blog

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.AfterClass
import org.koin.core.context.GlobalContext.stopKoin
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
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
