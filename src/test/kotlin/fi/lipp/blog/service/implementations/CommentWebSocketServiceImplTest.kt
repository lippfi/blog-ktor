package fi.lipp.blog.service.implementations

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.CommentWebSocketMessage
import fi.lipp.blog.data.ReactionDto
import fi.lipp.blog.data.webSocketJson
import fi.lipp.blog.domain.CommentEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.data.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentWebSocketServiceImplTest : UnitTestBase() {

    @Test
    fun testPersonalization() = runBlocking {
        val (ownerId, ownerLogin) = transaction {
            val userId = Users.insertAndGetId {
                it[email] = "owner@test.com"
                it[nickname] = "owner"
                it[password] = "pass"
                it[registrationTime] = Clock.System.now()
                it[sex] = Sex.UNDEFINED
                it[nsfw] = NSFWPolicy.HIDE
                it[timezone] = "UTC"
                it[language] = Language.EN
            }
            val diaryLogin = "owner-diary"
            Diaries.insert {
                it[owner] = userId
                it[login] = diaryLogin
                it[name] = "Owner's Diary"
                it[subtitle] = "Subtitle"
                it[defaultReadGroup] = groupService.everyoneGroupUUID
                it[defaultCommentGroup] = groupService.everyoneGroupUUID
                it[defaultReactGroup] = groupService.everyoneGroupUUID
                it[type] = DiaryType.PERSONAL
            }
            userId.value to diaryLogin
        }

        val postId = transaction {
            val diaryId = Diaries.select { Diaries.login eq ownerLogin }.first()[Diaries.id]
            Posts.insertAndGetId {
                it[authorType] = PostAuthorType.LOCAL
                it[localAuthor] = ownerId
                it[diary] = diaryId
                it[title] = "Test Post"
                it[uri] = "test-post"
                it[text] = "Hello"
                it[creationTime] = Clock.System.now()
                it[readGroup] = groupService.everyoneGroupUUID
                it[commentGroup] = groupService.everyoneGroupUUID
                it[reactionGroup] = groupService.privateGroupUUID
                it[commentReactionGroup] = groupService.privateGroupUUID
                it[isPreface] = false
                it[isEncrypted] = false
                it[isArchived] = false
                it[isHidden] = false
                it[classes] = ""
                it[avatar] = ""
            }.value
        }

        val commentEntity = transaction {
            val diaryId = Diaries.select { Diaries.login eq ownerLogin }.first()[Diaries.id]
            val postIdValue = Posts.select { (Posts.diary eq diaryId) and (Posts.uri eq "test-post") }.first()[Posts.id]

            val id = Comments.insertAndGetId {
                it[post] = postIdValue
                it[authorType] = CommentAuthorType.LOCAL
                it[localAuthor] = ownerId
                it[avatar] = "avatar"
                it[text] = "Nice post"
                it[creationTime] = Clock.System.now()
            }
            CommentEntity.findById(id)!!
        }

        val ownerSession = mock<WebSocketSession>()
        val otherSession = mock<WebSocketSession>()

        val otherUserId = UUID.randomUUID()

        commentWebSocketService.addSession(postId, Viewer.Registered(ownerId), ownerSession)
        commentWebSocketService.addSession(postId, Viewer.Registered(otherUserId), otherSession)

        commentWebSocketService.notifyCommentAdded(commentEntity)

        // Wait a bit for GlobalScope.launch to finish
        var attempts = 0
        while (attempts < 50) {
            try {
                verify(ownerSession).send(any())
                verify(otherSession).send(any())
                break
            } catch (e: Throwable) {
                attempts++
                Thread.sleep(100)
            }
        }

        val ownerCaptor = argumentCaptor<Frame>()
        val otherCaptor = argumentCaptor<Frame>()

        verify(ownerSession).send(ownerCaptor.capture())
        verify(otherSession).send(otherCaptor.capture())

        val ownerMessage = webSocketJson.decodeFromString<CommentWebSocketMessage>((ownerCaptor.firstValue as Frame.Text).readText())
        val otherMessage = webSocketJson.decodeFromString<CommentWebSocketMessage>((otherCaptor.firstValue as Frame.Text).readText())

        assertTrue((ownerMessage as CommentWebSocketMessage.CommentAdded).comment.isReactable, "Owner should be able to react")
        assertFalse((otherMessage as CommentWebSocketMessage.CommentAdded).comment.isReactable, "Other user should not be able to react to private group")
    }
}
