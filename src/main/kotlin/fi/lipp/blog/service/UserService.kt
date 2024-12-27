package fi.lipp.blog.service

import fi.lipp.blog.data.*
import java.net.URL
import java.util.UUID

interface UserService {
    fun generateInviteCode(userId: Long): String

    fun signUp(user: UserDto.Registration, inviteCode: String)

    /**
     * @return JWT token
     */
    fun signIn(user: UserDto.Login): String
    
    fun getUserInfo(userId: Long): UserDto.ProfileInfo
    
    fun update(userId: Long, user: UserDto.Registration, oldPassword: String)

    /**
     * @param userIdentifier is either login, email or nickname (any unique user identifier)
     */
    fun sendPasswordResetEmail(userIdentifier: String)
    fun performPasswordReset(resetCode: String, newPassword: String)

    fun isEmailBusy(email: String): Boolean
    fun isLoginBusy(login: String): Boolean
    fun isNicknameBusy(nickname: String): Boolean

    fun getAvatars(userId: Long): List<BlogFile>
    fun getAvatarUrls(userId: Long): List<URL>
    fun reorderAvatars(userId: Long, permutation: List<UUID>)
    fun addAvatar(userId: Long, files: List<FileUploadData>)
    fun deleteAvatar(userId: Long, avatarId: UUID)
}