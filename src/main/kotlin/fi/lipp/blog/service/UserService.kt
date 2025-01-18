package fi.lipp.blog.service

import fi.lipp.blog.data.*
import java.util.UUID

interface UserService {
    fun generateInviteCode(userId: UUID): String

    fun signUp(user: UserDto.Registration, inviteCode: String)

    /**
     * @return JWT token
     */
    fun signIn(user: UserDto.Login): String
    
    fun getUserInfo(login: String): UserDto.ProfileInfo
    
    fun update(userId: UUID, user: UserDto.Registration, oldPassword: String)

    /**
     * @param userIdentifier is either login, email or nickname (any unique user identifier)
     */
    fun sendPasswordResetEmail(userIdentifier: String)
    fun performPasswordReset(resetCode: String, newPassword: String)

    fun isEmailBusy(email: String): Boolean
    fun isLoginBusy(login: String): Boolean
    fun isNicknameBusy(nickname: String): Boolean

    fun getAvatars(userId: UUID): List<BlogFile>
    fun getAvatarUrls(userId: UUID): List<String>
    fun reorderAvatars(userId: UUID, permutation: List<UUID>)
    fun addAvatar(userId: UUID, files: List<FileUploadData>)
    fun deleteAvatar(userId: UUID, avatarUri: String)
}