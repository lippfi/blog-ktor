package fi.lipp.blog.service

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.*
import kotlinx.datetime.LocalDate
import java.util.UUID
import kotlin.jvm.Throws

interface UserService {
    fun generateInviteCode(userId: UUID): String

    fun getCurrentSessionInfo(userId: UUID): UserDto.SessionInfo

    /**
     * First step of registration - creates a pending registration and sends confirmation email
     * @throws InviteCodeRequiredException if invite code is required but not provided
     * @throws InvalidInviteCodeException if provided invite code is invalid
     * @throws EmailIsBusyException if email is already in use
     * @throws LoginIsBusyException if login is already in use
     * @throws NicknameIsBusyException if nickname is already in use
     */
    @Throws(InviteCodeRequiredException::class, InvalidInviteCodeException::class, EmailIsBusyException::class, LoginIsBusyException::class, NicknameIsBusyException::class)
    fun signUp(user: UserDto.Registration, inviteCode: String)

    /**
     * Second step of registration - confirms email and creates a user
     * @param confirmationCode the confirmation code sent to the user's email
     * @return JWT token for the newly created user
     * @throws ConfirmationCodeInvalidOrExpiredException if confirmation code is invalid or expired
     */
    @Throws(ConfirmationCodeInvalidOrExpiredException::class)
    fun confirmRegistration(confirmationCode: String): String

    /**
     * @return JWT token
     */
    @Throws(UserNotFoundException::class, WrongPasswordException::class)
    fun signIn(user: UserDto.Login): String


    fun getUserInfo(login: String): UserDto.ProfileInfo

    /**
     * @param userIdentifier is either login, email or nickname (any unique user identifier)
     */
    fun sendPasswordResetEmail(userIdentifier: String)
    fun performPasswordReset(resetCode: String, newPassword: String)

    /**
     * Check if email is already in use by a registered user
     */
    fun isEmailBusy(email: String): Boolean

    /**
     * Check if login is already in use by a registered user
     */
    fun isLoginBusy(login: String): Boolean

    /**
     * Check if nickname is already in use by a registered user
     */
    fun isNicknameBusy(nickname: String): Boolean

    fun getAvatars(userId: UUID): List<BlogFile>
    fun getAvatarUrls(userId: UUID): List<String>
    fun reorderAvatars(userId: UUID, permutation: List<UUID>)
    fun addAvatar(userId: UUID, files: List<FileUploadData>): List<String>
    fun deleteAvatar(userId: UUID, avatarUri: String)
    fun changePrimaryAvatar(userId: UUID, avatarUri: String)
    fun uploadAvatar(userId: UUID, avatarUri: String)

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

    /**
     * Follow another user
     * @throws UserNotFoundException if target user doesn't exist
     * @throws AlreadyFollowingException if already following the user
     */
    @Throws(UserNotFoundException::class, AlreadyFollowingException::class)
    fun followUser(userId: UUID, targetLogin: String)

    /**
     * Unfollow a user
     * @throws NotFollowingException if not following the user
     */
    @Throws(NotFollowingException::class)
    fun unfollowUser(userId: UUID, targetLogin: String)

    /**
     * Get list of users that the specified user is following
     */
    fun getFollowing(userId: UUID): List<UserDto.View>

    /**
     * Get list of users that follow the specified user
     */
    fun getFollowers(userId: UUID): List<UserDto.View>

    /**
     * Get or create the system user
     * @return ID of the system user
     */
    fun getOrCreateSystemUser(): UUID

    /**
     * Get the language preference for a user.
     * 
     * @param userId The ID of the user
     * @return The user's language preference, or null if the user doesn't exist
     */
    fun getUserLanguage(userId: UUID): Language?

    fun update(userId: UUID, user: UserDto.Registration, oldPassword: String)
    fun updateAdditionalInfo(userId: UUID, info: UserDto.AdditionalInfo)

    /**
     * First step of email update - creates a pending email change and sends confirmation email
     * @throws EmailIsBusyException if email is already in use
     */
    @Throws(EmailIsBusyException::class)
    fun updateEmail(userId: UUID, email: String)

    /**
     * Second step of email update - confirms email change
     * @param confirmationCode the confirmation code sent to the user's email
     * @throws ConfirmationCodeInvalidOrExpiredException if confirmation code is invalid or expired
     */
    @Throws(ConfirmationCodeInvalidOrExpiredException::class)
    fun confirmEmailUpdate(confirmationCode: String)
    fun updateNickname(userId: UUID, nickname: String)
    fun updatePassword(userId: UUID, newPassword: String, oldPassword: String)
    fun updateSex(userId: UUID, sex: Sex)
    fun updateTimezone(userId: UUID, timezone: String)
    fun updateLanguage(userId: UUID, language: Language)
    fun updateNSFWPolicy(userId: UUID, nsfw: NSFWPolicy)
    fun updateBirthDate(userId: UUID, birthDate: LocalDate)
    /**
     * Update user's notification settings
     */
    fun updateNotificationSettings(userId: UUID, settings: NotificationSettings)
}
