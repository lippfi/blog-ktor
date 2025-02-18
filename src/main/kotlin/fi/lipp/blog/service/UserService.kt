package fi.lipp.blog.service

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.*
import java.util.UUID
import kotlin.jvm.Throws

interface UserService {
    fun generateInviteCode(userId: UUID): String

    @Throws(InviteCodeRequiredException::class, InvalidInviteCodeException::class, EmailIsBusyException::class, LoginIsBusyException::class, NicknameIsBusyException::class)
    fun signUp(user: UserDto.Registration, inviteCode: String)

    /**
     * @return JWT token
     */
    @Throws(UserNotFoundException::class, WrongPasswordException::class)
    fun signIn(user: UserDto.Login): String

    fun updateAdditionalInfo(userId: UUID, info: UserDto.AdditionalInfo)

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
    fun changePrimaryAvatar(userId: UUID, avatarUri: String)
    fun uploadAvatar(userId: UUID, avatarUri: String)

    /**
     * Update user's notification settings
     */
    fun updateNotificationSettings(userId: UUID, settings: NotificationSettings)

    /**
     * Send a friend request to another user
     * @throws UserNotFoundException if target user doesn't exist
     * @throws FriendRequestAlreadyExistsException if request already exists
     * @throws AlreadyFriendsException if users are already friends
     */
    @Throws(UserNotFoundException::class, FriendRequestAlreadyExistsException::class, AlreadyFriendsException::class)
    fun sendFriendRequest(userId: UUID, request: FriendRequestDto.Create)

    /**
     * Accept a friend request
     * @throws FriendRequestNotFoundException if request doesn't exist
     * @throws NotRequestRecipientException if user is not the recipient of the request
     */
    @Throws(FriendRequestNotFoundException::class, NotRequestRecipientException::class)
    fun acceptFriendRequest(userId: UUID, requestId: UUID, label: String?)

    /**
     * Decline a friend request
     * @throws FriendRequestNotFoundException if request doesn't exist
     * @throws NotRequestRecipientException if user is not the recipient of the request
     */
    @Throws(FriendRequestNotFoundException::class, NotRequestRecipientException::class)
    fun declineFriendRequest(userId: UUID, requestId: UUID)

    /**
     * Update label for a friend
     * @throws NotFriendsException if users are not friends
     */
    @Throws(NotFriendsException::class)
    fun updateFriendLabel(userId: UUID, friendLogin: String, label: String?)

    /**
     * Cancel an outgoing friend request
     * @throws FriendRequestNotFoundException if request doesn't exist
     * @throws NotRequestSenderException if user is not the sender of the request
     */
    @Throws(FriendRequestNotFoundException::class, NotRequestSenderException::class)
    fun cancelFriendRequest(userId: UUID, requestId: UUID)

    /**
     * Get list of friend requests sent by user
     */
    fun getSentFriendRequests(userId: UUID): List<FriendRequestDto>

    /**
     * Get list of friend requests received by user
     */
    fun getReceivedFriendRequests(userId: UUID): List<FriendRequestDto>

    /**
     * Get list of user's friends
     */
    fun getFriends(userId: UUID): List<UserDto.View>

    /**
     * Remove friend from friends list
     * @throws NotFriendsException if users are not friends
     */
    @Throws(NotFriendsException::class)
    fun removeFriend(userId: UUID, friendLogin: String)

    /**
     * Get basic user information for display
     * @param userId ID of the user
     * @return User view with basic information
     */
    fun getUserView(userId: UUID): UserDto.View
}
