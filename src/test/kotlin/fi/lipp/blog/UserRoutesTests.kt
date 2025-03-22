package fi.lipp.blog

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import fi.lipp.blog.data.*
import fi.lipp.blog.data.FriendRequestDto
import fi.lipp.blog.plugins.USER_ID
import fi.lipp.blog.plugins.configureRouting
import fi.lipp.blog.plugins.configureSecurity
import fi.lipp.blog.plugins.configureSerialization
import fi.lipp.blog.service.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.AfterTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

class UserRoutesTests {
    private lateinit var userService: UserService
    private lateinit var reactionService: ReactionService
    private val testUserId = UUID.randomUUID()
    private val jwtSecret = "test-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"

    private val json = Json {
        serializersModule = SerializersModule {
            contextual(UUIDSerializer)
        }
    }
    private val testToken = JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .withClaim(USER_ID, testUserId.toString())
        .sign(Algorithm.HMAC256(jwtSecret))

    @Before
    fun setUp() {
        userService = mock(UserService::class.java)
        reactionService = mock(ReactionService::class.java)

        startKoin {
            modules(module {
                single { userService }
                single { reactionService }
                // Mock other required services
                single { mock(NotificationService::class.java) }
                single { mock(DialogService::class.java) }
                single { mock(PostService::class.java) }
                single { mock(DiaryService::class.java) }
                single { mock(StorageService::class.java) }
                single { mock(AccessGroupService::class.java) }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `test get recent reactions - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        // Configure the test application
        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        // Mock the reaction service response
        val mockReactions = listOf(
            ReactionDto.View("happy", "happy.png"),
            ReactionDto.View("sad", "sad.png")
        )
        `when`(reactionService.getUserRecentReactions(testUserId, 50)).thenReturn(mockReactions)

        // Test with default limit
        client.get("/user/recent-reactions") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = json.decodeFromString<List<ReactionDto.View>>(bodyAsText())
            assertEquals(2, response.size)
            assertEquals(mockReactions[0].name, response[0].name)
            assertEquals(mockReactions[1].name, response[1].name)
        }

        // Test with custom limit
        val customLimit = 1
        `when`(reactionService.getUserRecentReactions(testUserId, customLimit))
            .thenReturn(mockReactions.take(customLimit))

        client.get("/user/recent-reactions?limit=$customLimit") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = json.decodeFromString<List<ReactionDto.View>>(bodyAsText())
            assertEquals(customLimit, response.size)
            assertEquals(mockReactions[0].name, response[0].name)
        }
    }

    @Test
    fun `test get recent reactions - unauthenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        client.get("/user/recent-reactions").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test send friend request - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        val request = FriendRequestDto.Create("somelogin", "Let's be friends!", "coworker")
        client.post("/user/friend-request") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(FriendRequestDto.Create.serializer(), request))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Friend request sent successfully", bodyAsText())
        }
    }

    @Test
    fun `test accept friend request - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        val requestId = UUID.randomUUID()
        client.post("/user/friend-request/$requestId/accept") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Friend request accepted", bodyAsText())
        }
    }

    @Test
    fun `test decline friend request - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        val requestId = UUID.randomUUID()
        client.post("/user/friend-request/$requestId/decline") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Friend request declined", bodyAsText())
        }
    }

    @Test
    fun `test get friends - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        val mockFriends = listOf(
            UserDto.View(
                login = "friend1",
                nickname = "Friend One",
                avatarUri = null
            )
        )
        `when`(userService.getFriends(testUserId)).thenReturn(mockFriends)

        client.get("/user/friends") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val response = json.decodeFromString<List<UserDto.View>>(bodyAsText())
            assertEquals(1, response.size)
            assertEquals(mockFriends[0].login, response[0].login)
            assertEquals(mockFriends[0].nickname, response[0].nickname)
        }
    }

    @Test
    fun `test remove friend - authenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        val friendId = UUID.randomUUID()
        client.delete("/user/friends/$friendId") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Friend removed successfully", bodyAsText())
        }
    }

    @Test
    fun `test friend operations - unauthenticated`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to jwtSecret,
                "jwt.issuer" to jwtIssuer,
                "jwt.audience" to jwtAudience,
                "jwt.realm" to "test-realm"
            )
        }

        application {
            configureSerialization()
            configureSecurity()
            configureRouting()
        }

        // Test send friend request
        client.post("/user/friend-request") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        // Test accept friend request
        client.post("/user/friend-request/${UUID.randomUUID()}/accept").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        // Test decline friend request
        client.post("/user/friend-request/${UUID.randomUUID()}/decline").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        // Test get friends
        client.get("/user/friends").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        // Test remove friend
        client.delete("/user/friends/${UUID.randomUUID()}").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}
