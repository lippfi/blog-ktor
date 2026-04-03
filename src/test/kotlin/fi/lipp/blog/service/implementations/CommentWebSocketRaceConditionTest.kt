package fi.lipp.blog.service.implementations

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.CommentWebSocketMessage
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
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import org.mockito.kotlin.*
import java.util.*
import kotlin.test.assertTrue

class CommentWebSocketRaceConditionTest : UnitTestBase() {

    @Test
    fun testNotifyInsideTransaction() = runBlocking {
        val (ownerId, ownerLogin) = transaction {
            val userId = Users.insertAndGetId {
                it[email] = "race@test.com"
                it[nickname] = "race"
                it[password] = "pass"
                it[registrationTime] = Clock.System.now()
                it[sex] = Sex.UNDEFINED
                it[nsfw] = NSFWPolicy.HIDE
                it[timezone] = "UTC"
                it[language] = Language.EN
            }
            val diaryLogin = "race-diary"
            Diaries.insert {
                it[owner] = userId
                it[login] = diaryLogin
                it[name] = "Race Diary"
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
                it[uri] = "race-post"
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

        val session = mock<WebSocketSession>()
        commentWebSocketService.addSession(postId, Viewer.Registered(ownerId), session)

        // Simulating production behavior: calling notify inside the transaction
        transaction {
            val id = Comments.insertAndGetId {
                it[post] = postId
                it[authorType] = CommentAuthorType.LOCAL
                it[localAuthor] = ownerId
                it[avatar] = "avatar"
                it[text] = "Race comment"
                it[creationTime] = Clock.System.now()
            }
            val commentEntity = CommentEntity.findById(id)!!
            
            println("[DEBUG_LOG] Calling notifyCommentAdded inside transaction")
            commentWebSocketService.notifyCommentAdded(commentEntity)
            
            // Stay inside transaction for a bit to ensure the coroutine tries to fetch
            Thread.sleep(1000)
            println("[DEBUG_LOG] Finished Thread.sleep inside transaction")
        }

        // Now wait for the notification
        var received = false
        var attempts = 0
        while (attempts < 20) {
            try {
                verify(session).send(any())
                received = true
                break
            } catch (e: Throwable) {
                attempts++
                Thread.sleep(200)
            }
        }

        assertTrue(received, "Should have received a message even if notified inside transaction (or we need to fix it so it works)")
    }
}
