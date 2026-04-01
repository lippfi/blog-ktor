package fi.lipp.blog.service

import fi.lipp.blog.data.DeviceSessionDto
import fi.lipp.blog.data.TokenPair
import java.util.UUID

interface SessionService {
    /**
     * Creates a new session for the user and returns an access/refresh token pair.
     * @param userId The ID of the authenticated user
     * @param deviceName The name/user-agent of the device
     * @param location The IP address or location of the device
     * @param isMobile Whether the device is a mobile device
     * @return A pair of short-lived access token and long-lived refresh token
     */
    fun createSession(userId: UUID, deviceName: String, location: String, isMobile: Boolean): TokenPair

    /**
     * Refreshes the access token using a valid refresh token.
     * Also updates the session's last seen timestamp.
     * @param refreshToken The refresh token from the existing session
     * @return A new token pair with a fresh access token and the same refresh token
     */
    fun refreshSession(refreshToken: String): TokenPair

    /**
     * Revokes a specific session by its ID. Only the session owner can revoke it.
     * @param userId The ID of the user requesting revocation
     * @param sessionId The ID of the session to revoke
     */
    fun revokeSession(userId: UUID, sessionId: UUID)

    /**
     * Revokes all sessions for a user except the current one.
     * @param userId The ID of the user
     * @param currentSessionId The ID of the session to keep active
     */
    fun revokeOtherSessions(userId: UUID, currentSessionId: UUID)

    /**
     * Revokes all sessions for a user (e.g., on password change).
     * @param userId The ID of the user
     */
    fun revokeAllSessions(userId: UUID)

    /**
     * Lists all active (non-revoked) sessions for a user.
     * @param userId The ID of the user
     * @param currentSessionId The ID of the current session (to mark it in the response)
     * @return A list of active device sessions
     */
    fun getActiveSessions(userId: UUID, currentSessionId: UUID): List<DeviceSessionDto>

    /**
     * Checks whether a session is still valid (exists and not revoked).
     * @param sessionId The ID of the session to check
     * @return true if the session is active, false otherwise
     */
    fun isSessionValid(sessionId: UUID): Boolean
}
