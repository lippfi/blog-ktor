package fi.lipp.blog

import fi.lipp.blog.model.exceptions.SessionNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.UserSessions
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SessionServiceTests : UnitTestBase() {

    @Test
    fun `create session returns token pair`() {
        transaction {
            val (userId, _) = signUsersUp()
            val tokenPair = sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)

            assertNotNull(tokenPair.accessToken)
            assertNotNull(tokenPair.refreshToken)
            assertTrue(tokenPair.accessToken.isNotEmpty())
            assertTrue(tokenPair.refreshToken.isNotEmpty())

            rollback()
        }
    }

    @Test
    fun `refresh session returns new tokens with rotated refresh token`() {
        transaction {
            val (userId, _) = signUsersUp()
            val tokenPair = sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)
            val refreshedPair = sessionService.refreshSession(tokenPair.refreshToken)

            assertNotNull(refreshedPair.accessToken)
            assertNotNull(refreshedPair.refreshToken)
            assertNotEquals(tokenPair.refreshToken, refreshedPair.refreshToken)

            rollback()
        }
    }

    @Test
    fun `old refresh token is invalid after rotation`() {
        transaction {
            val (userId, _) = signUsersUp()
            val tokenPair = sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)
            val refreshedPair = sessionService.refreshSession(tokenPair.refreshToken)

            // Old refresh token should no longer work
            assertFailsWith<SessionNotFoundException> {
                sessionService.refreshSession(tokenPair.refreshToken)
            }

            // New refresh token should work
            val secondRefresh = sessionService.refreshSession(refreshedPair.refreshToken)
            assertNotNull(secondRefresh.accessToken)
            assertNotEquals(refreshedPair.refreshToken, secondRefresh.refreshToken)

            rollback()
        }
    }

    @Test
    fun `refresh with invalid token throws exception`() {
        assertFailsWith<SessionNotFoundException> {
            sessionService.refreshSession("invalid-refresh-token")
        }
    }

    @Test
    fun `revoke session makes it invalid`() {
        transaction {
            val (userId, _) = signUsersUp()
            // signUsersUp creates sessions via confirmRegistration, revoke them first
            sessionService.revokeAllSessions(userId)

            val tokenPair = sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)

            val sessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertEquals(1, sessions.size)
            val sessionId = sessions[0].id

            assertTrue(sessionService.isSessionValid(sessionId))
            sessionService.revokeSession(userId, sessionId)
            assertFalse(sessionService.isSessionValid(sessionId))

            rollback()
        }
    }

    @Test
    fun `revoke other user session throws exception`() {
        transaction {
            val (userId1, userId2) = signUsersUp()
            sessionService.revokeAllSessions(userId1)

            val tokenPair = sessionService.createSession(userId1, "Chrome/120", "192.168.1.1", false)

            val sessions = sessionService.getActiveSessions(userId1, java.util.UUID.randomUUID())
            val sessionId = sessions[0].id

            assertFailsWith<WrongUserException> {
                sessionService.revokeSession(userId2, sessionId)
            }

            rollback()
        }
    }

    @Test
    fun `revoke others keeps current session`() {
        transaction {
            val (userId, _) = signUsersUp()
            sessionService.revokeAllSessions(userId)

            sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)
            sessionService.createSession(userId, "Firefox/110", "10.0.0.1", false)
            sessionService.createSession(userId, "Safari/17", "172.16.0.1", false)

            val allSessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertEquals(3, allSessions.size)

            val currentSessionId = allSessions[0].id
            sessionService.revokeOtherSessions(userId, currentSessionId)

            val remaining = sessionService.getActiveSessions(userId, currentSessionId)
            assertEquals(1, remaining.size)
            assertEquals(currentSessionId, remaining[0].id)
            assertTrue(remaining[0].isCurrent)

            rollback()
        }
    }

    @Test
    fun `revoke all sessions`() {
        transaction {
            val (userId, _) = signUsersUp()
            sessionService.revokeAllSessions(userId)

            sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)
            sessionService.createSession(userId, "Firefox/110", "10.0.0.1", false)

            val allSessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertEquals(2, allSessions.size)

            sessionService.revokeAllSessions(userId)

            val remaining = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertEquals(0, remaining.size)

            rollback()
        }
    }

    @Test
    fun `get active sessions shows device info`() {
        transaction {
            val (userId, _) = signUsersUp()
            sessionService.revokeAllSessions(userId)

            sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)

            val sessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertEquals(1, sessions.size)

            val session = sessions[0]
            assertEquals("Chrome/120", session.deviceName)
            assertEquals("192.168.1.1", session.location)
            assertNotNull(session.firstSeen)
            assertNotNull(session.lastSeen)
            assertFalse(session.isCurrent)

            val sessionsWithCurrent = sessionService.getActiveSessions(userId, session.id)
            assertTrue(sessionsWithCurrent[0].isCurrent)

            rollback()
        }
    }

    @Test
    fun `refresh revoked session throws exception`() {
        val (userId, _) = signUsersUp()
        val tokenPair = sessionService.createSession(userId, "Chrome/120", "192.168.1.1", false)

        val sessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
        val session = sessions.first { it.deviceName == "Chrome/120" }
        sessionService.revokeSession(userId, session.id)

        assertFailsWith<SessionNotFoundException> {
            sessionService.refreshSession(tokenPair.refreshToken)
        }
    }

    @Test
    fun `sign in creates session`() {
        transaction {
            val (userId, _) = signUsersUp()

            val tokenPair = userService.signIn(
                fi.lipp.blog.data.UserDto.Login(testUser.login, testUser.password),
                "TestBrowser/1.0",
                "10.0.0.5",
                false
            )

            assertNotNull(tokenPair.accessToken)
            assertNotNull(tokenPair.refreshToken)

            val sessions = sessionService.getActiveSessions(userId, java.util.UUID.randomUUID())
            assertTrue(sessions.any { it.deviceName == "TestBrowser/1.0" && it.location == "10.0.0.5" })

            rollback()
        }
    }
}
