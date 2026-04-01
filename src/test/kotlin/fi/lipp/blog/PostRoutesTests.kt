package fi.lipp.blog

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.model.Page
import fi.lipp.blog.plugins.*
import fi.lipp.blog.service.*
import fi.lipp.blog.util.UUIDSerializer
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PostRoutesTests {
    private lateinit var postService: PostService
    private val testUserId = UUID.randomUUID()
    private val jwtSecret = "test-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"

    private val json = Json {
        serializersModule = SerializersModule {
            contextual(UUIDSerializer)
        }
    }
    private val testSessionId = UUID.randomUUID()
    private val testToken = JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .withClaim(USER_ID, testUserId.toString())
        .withClaim(SESSION_ID, testSessionId.toString())
        .sign(Algorithm.HMAC256(jwtSecret))

    private lateinit var sessionService: SessionService

    @Before
    fun setUp() {
        postService = mock()
        sessionService = mock()
        whenever(sessionService.isSessionValid(testSessionId)).thenReturn(true)

        startKoin {
            modules(module {
                single { postService }
                single { sessionService }
                single { mock<UserService>() }
                single { mock<ReactionService>() }
                single { mock<NotificationService>() }
                single { mock<DialogService>() }
                single { mock<DiaryService>() }
                single { mock<StorageService>() }
                single { mock<AccessGroupService>() }
                single { mock<CommentWebSocketService>() }
                single<GeoLocationService> { fi.lipp.blog.stubs.GeoLocationServiceStub() }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `test get hidden posts route`() = testApplication {
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
            configureWebSockets()
            configureRouting()
        }

        val diaryLogin = "test-diary"
        val mockPosts = Page<PostDto.View>(
            content = listOf(
                PostDto.View(
                    id = UUID.randomUUID(),
                    uri = "hidden-post",
                    avatar = "avatar",
                    authorLogin = "author",
                    diaryLogin = diaryLogin,
                    authorNickname = "nickname",
                    authorSignature = null,
                    title = "Hidden Post",
                    text = "Secret content",
                    creationTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                    isPreface = false,
                    isEncrypted = false,
                    isHidden = true,
                    classes = "",
                    tags = emptySet(),
                    isReactable = true,
                    reactions = emptyList(),
                    isCommentable = true,
                    commentsCount = 0,
                    readGroupId = UUID.randomUUID(),
                    commentGroupId = UUID.randomUUID(),
                    reactionGroupId = UUID.randomUUID(),
                    commentReactionGroupId = UUID.randomUUID()
                )
            ),
            currentPage = 1,
            totalPages = 1
        )

        whenever(postService.getHiddenPosts(eq(testUserId), eq(diaryLogin), any())).thenReturn(mockPosts)

        val response = client.get("/posts/hidden") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            parameter("diary", diaryLogin)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test get friends posts route`() = testApplication {
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
            configureWebSockets()
            configureRouting()
        }

        val mockPosts = Page<PostDto.View>(
            content = listOf(
                PostDto.View(
                    id = UUID.randomUUID(),
                    uri = "friends-post",
                    avatar = "avatar",
                    authorLogin = "friend",
                    diaryLogin = "friend-diary",
                    authorNickname = "friend-nick",
                    authorSignature = null,
                    title = "Friends Post",
                    text = "Friend content",
                    creationTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                    isPreface = false,
                    isEncrypted = false,
                    isHidden = false,
                    classes = "",
                    tags = emptySet(),
                    isReactable = true,
                    reactions = emptyList(),
                    isCommentable = true,
                    commentsCount = 0,
                    readGroupId = UUID.randomUUID(),
                    commentGroupId = UUID.randomUUID(),
                    reactionGroupId = UUID.randomUUID(),
                    commentReactionGroupId = UUID.randomUUID()
                )
            ),
            currentPage = 0,
            totalPages = 1
        )

        whenever(postService.getFriendsPosts(eq(testUserId), any())).thenReturn(mockPosts)

        val response = client.get("/posts/friends") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
