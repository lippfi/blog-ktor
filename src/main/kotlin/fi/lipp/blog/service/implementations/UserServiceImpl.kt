package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.plugins.createJwtToken
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.*
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.Throws

class UserServiceImpl(
    private val encoder: PasswordEncoder,
    private val mailService: MailService,
    private val storageService: StorageService,
    private val accessGroupService: AccessGroupService,
    private val notificationService: NotificationService,
) : UserService {
    override fun generateInviteCode(userId: UUID): String {
        val inviteCode = transaction {
            InviteCodes.insertAndGetId {
                it[creator] = userId
                it[issuedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
        return inviteCode.value.toString()
    }

    @Throws(InviteCodeRequiredException::class, InvalidInviteCodeException::class, EmailIsBusyException::class, LoginIsBusyException::class, NicknameIsBusyException::class)
    override fun signUp(user: UserDto.Registration, inviteCode: String) {
        val inviteCodeEntity = transaction {
            if (inviteCode.isEmpty()) {
                if (Users.selectAll().count() > 0) throw InviteCodeRequiredException()
                null
            } else {
                val uuid = UUID.fromString(inviteCode)
                val inviteCodeEntity = InviteCodeEntity.findById(uuid)
                if (inviteCodeEntity == null || !inviteCodeEntity.isValid) throw InvalidInviteCodeException()
                inviteCodeEntity
            }
        }

        val timezoneParsed = try {
            kotlinx.datetime.TimeZone.of(user.timezone)
        } catch (e: Exception) {
            throw InvalidTimezoneException()
        }

        if (isEmailBusy(user.email)) throw EmailIsBusyException()
        if (isLoginBusy(user.login)) throw LoginIsBusyException()
        if (isNicknameBusy(user.nickname)) throw NicknameIsBusyException()
        transaction {
            val userId = Users.insertAndGetId {
                it[email] = user.email
                it[password] = encoder.encode(user.password)
                it[nickname] = user.nickname
                it[Users.inviteCode] = inviteCodeEntity?.id

                it[sex] = Sex.UNDEFINED
                it[nsfw] = NSFWPolicy.HIDE
                it[timezone] = timezoneParsed.id
                it[language] = user.language
            }
            Diaries.insert {
                it[name] = "Unnamed blog"
                it[subtitle] = ""
                it[login] = user.login
                it[owner] = userId
                it[type] = DiaryType.PERSONAL
                it[defaultReadGroup] = accessGroupService.everyoneGroupUUID
                it[defaultCommentGroup] = accessGroupService.registeredGroupUUID
            }
        }
    }

    override fun signIn(user: UserDto.Login): String {
        val userEntity = getUserByLogin(user.login) ?: throw UserNotFoundException()
        if (!encoder.matches(user.password, userEntity.password)) {
            throw WrongPasswordException()
        }
        return createJwtToken(userEntity.id.value)
    }

    override fun updateAdditionalInfo(userId: UUID, info: UserDto.AdditionalInfo) {
        val timezoneParsed = try {
            kotlinx.datetime.TimeZone.of(info.timezone)
        } catch (e: Exception) {
            throw InvalidTimezoneException()
        }
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                sex = info.sex
                nsfw = info.nsfw
                timezone = timezoneParsed.id
                language = info.language
                birthdate = info.birthDate
            }
        }
    }

    override fun getUserView(userId: UUID): UserDto.View {
        return transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            val userDiary = DiaryEntity.find { (Diaries.owner eq userId) and (Diaries.type eq DiaryType.PERSONAL) }.singleOrNull() ?: throw DiaryNotFoundException()
            val primaryAvatarUri = userEntity.primaryAvatar?.let { FileEntity.findById(it.value) }?.toBlogFile()?.let { storageService.getFileURL(it) }
            UserDto.View(
                login = userDiary.login,
                nickname = userEntity.nickname,
                avatarUri = primaryAvatarUri,
            )
        }
    }

    override fun getUserInfo(login: String): UserDto.ProfileInfo {
        return transaction { 
            val diaryEntity = DiaryEntity.find { Diaries.login eq login }.singleOrNull() ?: throw DiaryNotFoundException()
            val userEntity = UserEntity.findById(diaryEntity.owner) ?: throw UserNotFoundException()
            UserDto.ProfileInfo(
                login = diaryEntity.login,
                email = userEntity.email,
                nickname = userEntity.nickname,
                registrationTime = userEntity.registrationTime,
                notificationSettings = NotificationSettings(
                    notifyAboutComments = userEntity.notifyAboutComments,
                    notifyAboutReplies = userEntity.notifyAboutReplies,
                    notifyAboutPostReactions = userEntity.notifyAboutPostReactions,
                    notifyAboutCommentReactions = userEntity.notifyAboutCommentReactions,
                    notifyAboutPrivateMessages = userEntity.notifyAboutPrivateMessages
                )
            )
        }
    }

    override fun update(userId: UUID, user: UserDto.Registration, oldPassword: String) {
        val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
        if (!encoder.matches(oldPassword, userEntity.password)) throw WrongPasswordException()

        if (userEntity.email != user.email && isEmailBusy(user.email)) throw EmailIsBusyException()
        if (userEntity.nickname != user.nickname && isNicknameBusy(user.nickname)) throw NicknameIsBusyException()

        // Login change is turned off 
//        val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
//        if (diaryEntity.login != user.login && isLoginBusy(user.login)) throw LoginIsBusyException()

        transaction {
            userEntity.apply {
                email = user.email
                password = encoder.encode(user.password)
                nickname = user.nickname
            }
           // Login change is turned off 
//            diaryEntity.apply {
//                login = user.login
//            }
        }
    }

    override fun sendPasswordResetEmail(userIdentifier: String) {
        val userEntity = getUserByLogin(userIdentifier)
            ?: getUserByEmail(userIdentifier)
            ?: getUserByNickname(userIdentifier)
            ?: throw UserNotFoundException()

        val resetCode = transaction {
            PasswordResets.insertAndGetId {
                it[user] = userEntity.id
                it[issuedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }

        mailService.sendEmail(
            subject = "Password Reset",
            text = """
                You recently requested to reset your password. Please use the code below to proceed with setting a new password:

                Reset code: $resetCode

                Enter this code on the password reset page to continue. This code will expire in 30 minutes for security reasons.
                If you did not request a password reset, please ignore this email and do not share this code with anyone. Your password remains secure as long as this code stays private.
            """.trimMargin(),
            userEntity.email
        )
    }

    override fun performPasswordReset(resetCode: String, newPassword: String) {
        val userEntity = transaction {
            val passwordResetCodeEntity = PasswordResetCodeEntity.findById(UUID.fromString(resetCode))?.takeIf { it.isValid } ?: throw ResetCodeInvalidOrExpiredException()
            val userEntity = UserEntity.findById(passwordResetCodeEntity.userId) ?: throw RuntimeException("Reset code is bound to nonexistent user")
            userEntity.apply {
                password = encoder.encode(newPassword)
            }
        }
        mailService.sendEmail(
            subject = "Password Change Notification",
            text = """
                Your password has been successfully changed. If you did not initiate this change, please ensure that your email access is secure and that no unauthorized parties can access your account. It is also recommended that you try to reset your password again immediately.

                If you requested this change, no further action is needed. For your security, please do not share your password with anyone.
            """.trimMargin(),
            userEntity.email
        )
    }

    override fun isEmailBusy(email: String): Boolean = getUserByEmail(email) != null
    override fun isLoginBusy(login: String): Boolean = getDiaryByLogin(login) != null
    override fun isNicknameBusy(nickname: String): Boolean = getUserByNickname(nickname) != null

    private fun getUserByEmail(email: String): UserEntity? {
        return transaction { UserEntity.find { Users.email eq email }.firstOrNull() }
    }
    private fun getDiaryAndUserByLogin(login: String): Pair<DiaryEntity, UserEntity>? {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq login }.firstOrNull()
            val userEntity = diaryEntity?.owner?.value?.let { UserEntity.findById(it) }
            if (userEntity != null) Pair(diaryEntity, userEntity) else null
        }
    }
    private fun getUserByLogin(login: String): UserEntity? {
        return getDiaryAndUserByLogin(login)?.second
    }
    private fun getDiaryByLogin(login: String): DiaryEntity? {
        return transaction { DiaryEntity.find { Diaries.login eq login }.firstOrNull() }
    }
    private fun getUserByNickname(nickname: String): UserEntity? {
        return transaction { UserEntity.find { Users.nickname eq nickname }.firstOrNull() }
    }

    override fun getAvatars(userId: UUID): List<BlogFile> {
        val avatars: List<FileEntity> = transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            UserAvatars.select { UserAvatars.user eq userEntity.id }
                .orderBy(UserAvatars.ordinal)
                .mapNotNull { FileEntity.findById(it[UserAvatars.avatar].value) }
        } 
        return avatars.map { it.toBlogFile() }
    }

    override fun getAvatarUrls(userId: UUID): List<String> {
        return getAvatars(userId).map { storageService.getFileURL(it) }
    }

    override fun reorderAvatars(userId: UUID, permutation: List<UUID>) {
        transaction {
            val currentUUIDs = UserAvatars
                .select { UserAvatars.user eq userId }
                .map { it[UserAvatars.avatar] }
            val permutationIds = permutation.map { EntityID(it, Files) }

            if (!permutationIds.all { it in currentUUIDs }) {
                throw WrongUserException()
            }

            val uuidsToDelete = (currentUUIDs.toSet() - permutationIds.toSet())
            if (uuidsToDelete.isNotEmpty()) {
                UserAvatars.deleteWhere {
                    (user eq userId) and (avatar inList uuidsToDelete)
                }
            }

            permutation.forEachIndexed { index, uuid ->
                UserAvatars.update({ (UserAvatars.user eq userId) and (UserAvatars.avatar eq uuid) }) {
                    it[ordinal] = index
                }
            }
        }
    }

    override fun addAvatar(userId: UUID, files: List<FileUploadData>) {
        val existingMaxOrdinal = transaction {
            UserAvatars.slice(UserAvatars.ordinal.max())
                .select { UserAvatars.user eq userId }
                .firstOrNull()
                ?.get(UserAvatars.ordinal.max()) ?: 0
        }

        val newAvatars = storageService.storeAvatars(userId, files)

        transaction {
            // Check if user has a primary avatar
            val user = UserEntity.findById(userId) ?: return@transaction
            val needsSetPrimary = user.primaryAvatar == null

            newAvatars.forEachIndexed { index, avatarFile ->
                UserAvatars.insert {
                    it[UserAvatars.user] = EntityID(userId, Users)
                    it[avatar] = EntityID(avatarFile.id, Files)
                    it[UserAvatars.ordinal] = existingMaxOrdinal + index + 1
                }

                // Set first avatar as primary if user doesn't have one
                if (needsSetPrimary && index == 0) {
                    user.primaryAvatar = EntityID(avatarFile.id, Files)
                }
            }
        }
    }

    override fun deleteAvatar(userId: UUID, avatarUri: String) {
        val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex()
        val avatarIdString = uuidRegex.find(avatarUri)?.value ?: return
        val avatarId = try {
            UUID.fromString(avatarIdString)
        } catch (e: IllegalArgumentException) {
            return
        }
        transaction {
            val user = UserEntity.findById(userId) ?: return@transaction

            // Delete the avatar from UserAvatars
            UserAvatars.deleteWhere { 
                (UserAvatars.user eq EntityID(userId, Users)) and 
                (avatar eq EntityID(avatarId, Files))
            }

            // If this was the primary avatar, update it
            if (user.primaryAvatar?.value == avatarId) {
                // Find the first remaining avatar
                val firstAvatar = UserAvatars
                    .slice(UserAvatars.avatar)
                    .select { UserAvatars.user eq EntityID(userId, Users) }
                    .orderBy(UserAvatars.ordinal)
                    .firstOrNull()
                    ?.get(UserAvatars.avatar)

                // Set it as primary or null if no avatars remain
                user.primaryAvatar = firstAvatar
            }
        }
    }

    override fun updateNotificationSettings(userId: UUID, settings: NotificationSettings) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                notifyAboutComments = settings.notifyAboutComments
                notifyAboutReplies = settings.notifyAboutReplies
                notifyAboutPostReactions = settings.notifyAboutPostReactions
                notifyAboutCommentReactions = settings.notifyAboutCommentReactions
                notifyAboutPrivateMessages = settings.notifyAboutPrivateMessages
            }
        }
    }

    override fun sendFriendRequest(userId: UUID, request: FriendRequestDto.Create) {
        transaction {
            val toUser = DiaryEntity.find { Diaries.login eq request.toUser }.singleOrNull()?.owner ?: throw UserNotFoundException()

            // Check if they are already friends
            val existingFriendship = FriendshipEntity.find {
                (Friends.user1 eq userId and (Friends.user2 eq toUser)) or
                (Friends.user1 eq toUser and (Friends.user2 eq userId))
            }.firstOrNull()
            if (existingFriendship != null) {
                throw AlreadyFriendsException()
            }

            val existingRequest = FriendRequestEntity.find {
                FriendRequests.fromUser eq userId and (FriendRequests.toUser eq toUser)
            }.firstOrNull()
            if (existingRequest != null) {
                throw FriendRequestAlreadyExistsException()
            }

            val incomingRequest = FriendRequestEntity.find {
                FriendRequests.fromUser eq toUser and (FriendRequests.toUser eq userId)
            }.firstOrNull()
            if (incomingRequest != null) {
                acceptFriendRequest(userId, incomingRequest.id.value, request.label)
            } else {
                val friendRequest = FriendRequestEntity.new {
                    this.fromUser = EntityID(userId, Users)
                    this.toUser = toUser
                    this.message = request.message
                    this.label = request.label
                    this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
                }

                val senderDiary = DiaryEntity.find { Diaries.owner eq userId }.single()
                notificationService.notifyAboutFriendRequest(toUser.value, friendRequest.id.value, senderDiary.login)
            }
        }
    }

    override fun acceptFriendRequest(userId: UUID, requestId: UUID, label: String?) {
        transaction {
            val request = FriendRequestEntity.findById(requestId) ?: throw FriendRequestNotFoundException()
            if (request.toUser.value != userId) {
                throw NotRequestRecipientException()
            }

            // Create friendship
            FriendshipEntity.new {
                user1 = request.fromUser
                user2 = request.toUser
                createdAt = LocalDateTime.now().toKotlinLocalDateTime()
            }

            // Save labels for both users
            FriendLabelEntity.new {
                user = request.toUser
                friend = request.fromUser
                this.label = label
                createdAt = LocalDateTime.now().toKotlinLocalDateTime()
            }

            FriendLabelEntity.new {
                user = request.fromUser
                friend = request.toUser
                this.label = request.label
                createdAt = LocalDateTime.now().toKotlinLocalDateTime()
            }

            notificationService.markFriendRequestNotificationAsRead(userId, requestId)
            request.delete()
        }
    }

    override fun declineFriendRequest(userId: UUID, requestId: UUID) {
        transaction {
            val request = FriendRequestEntity.findById(requestId) ?: throw FriendRequestNotFoundException()
            if (request.toUser.value != userId) {
                throw NotRequestRecipientException()
            }
            notificationService.markFriendRequestNotificationAsRead(userId, requestId)
            request.delete()
        }
    }

    override fun cancelFriendRequest(userId: UUID, requestId: UUID) {
        transaction {
            val request = FriendRequestEntity.findById(requestId) ?: throw FriendRequestNotFoundException()
            if (request.fromUser.value != userId) {
                throw NotRequestSenderException()
            }
            notificationService.markFriendRequestNotificationAsRead(request.toUser.value, requestId)
            request.delete()
        }
    }

    override fun getSentFriendRequests(userId: UUID): List<FriendRequestDto> {
        return transaction {
            FriendRequestEntity.find { FriendRequests.fromUser eq userId }
                .map { request ->
                    FriendRequestDto(
                        id = request.id.value,
                        user = getUserView(request.toUser.value),
                        message = request.message,
                        label = request.label,
                        createdAt = request.createdAt
                    )
                }
        }
    }

    override fun getReceivedFriendRequests(userId: UUID): List<FriendRequestDto> {
        return transaction {
            notificationService.readAllFriendRequestNotifications(userId)
            FriendRequestEntity.find { FriendRequests.toUser eq userId }
                .map { request ->
                    FriendRequestDto(
                        id = request.id.value,
                        user = getUserView(request.fromUser.value),
                        message = request.message,
                        label = request.label,
                        createdAt = request.createdAt
                    )
                }
        }
    }

    override fun getFriends(userId: UUID): List<UserDto.View> {
        return transaction {
            val friendsAsUser1 = Friends
                .slice(Friends.user2)
                .select { Friends.user1 eq userId }

            val friendsAsUser2 = Friends
                .slice(Friends.user1)
                .select { Friends.user2 eq userId }

            (Users innerJoin Diaries)
                .slice(Users.nickname, Diaries.login, Users.primaryAvatar)
                .select {
                    ((Users.id inSubQuery friendsAsUser1) or (Users.id inSubQuery friendsAsUser2)) and
                    (Diaries.owner eq Users.id) and
                    (Diaries.type eq DiaryType.PERSONAL)
                }
                .map { row ->
                    val primaryAvatarId = row[Users.primaryAvatar]?.value
                    val primaryAvatarUrl = primaryAvatarId?.let { avatarId ->
                        FileEntity.findById(avatarId)?.toBlogFile()?.let { storageService.getFileURL(it) }
                    }
                    UserDto.View(
                        login = row[Diaries.login],
                        nickname = row[Users.nickname],
                        avatarUri = primaryAvatarUrl
                    )
                }
        }
    }

    override fun removeFriend(userId: UUID, friendLogin: String) {
        transaction {
            val friend = getUserByLogin(friendLogin) ?: throw UserNotFoundException()
            val friendship = FriendshipEntity.find {
                (Friends.user1 eq userId and (Friends.user2 eq friend.id)) or
                (Friends.user1 eq friend.id and (Friends.user2 eq userId))
            }.firstOrNull() ?: throw NotFriendsException()

            friendship.delete()

            // Delete friend labels when removing friendship
            FriendLabelEntity.find {
                (FriendLabels.user eq userId and (FriendLabels.friend eq friend.id)) or
                (FriendLabels.user eq friend.id and (FriendLabels.friend eq userId))
            }.forEach { it.delete() }
        }
    }

    override fun updateFriendLabel(userId: UUID, friendLogin: String, label: String?) {
        transaction {
            val friend = getUserByLogin(friendLogin) ?: throw UserNotFoundException()

            // Verify they are friends
            val friendship = FriendshipEntity.find {
                (Friends.user1 eq userId and (Friends.user2 eq friend.id)) or
                (Friends.user1 eq friend.id and (Friends.user2 eq userId))
            }.firstOrNull() ?: throw NotFriendsException()

            // Update or create label
            val existingLabel = FriendLabelEntity.find {
                FriendLabels.user eq userId and (FriendLabels.friend eq friend.id)
            }.firstOrNull()

            if (existingLabel != null) {
                existingLabel.label = label
            } else {
                FriendLabelEntity.new {
                    this.user = EntityID(userId, Users)
                    this.friend = friend.id
                    this.label = label
                    this.createdAt = LocalDateTime.now().toKotlinLocalDateTime()
                }
            }
        }
    }

    private fun getDiaryByUserId(userId: UUID): DiaryEntity? {
        return transaction { DiaryEntity.find { Diaries.owner eq userId }.firstOrNull() }
    }

    override fun changePrimaryAvatar(userId: UUID, avatarUri: String) {
        val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex()
        val avatarIdString = uuidRegex.find(avatarUri)?.value ?: return
        val avatarId = try {
            UUID.fromString(avatarIdString)
        } catch (e: IllegalArgumentException) {
            return
        }

        transaction {
            val avatarExists = UserAvatars.select {
                (UserAvatars.user eq userId) and (UserAvatars.avatar eq avatarId)
            }.count() > 0

            if (avatarExists) {
                UserEntity.findById(userId)?.apply {
                    primaryAvatar = EntityID(avatarId, Files)
                }
            } else {
                val fileEntity = FileEntity.findById(avatarId) ?: return@transaction
                val file = storageService.getFile(fileEntity.toBlogFile())
                val fileUploadData = FileUploadData(file.name, file.inputStream())

                val newAvatars = storageService.storeAvatars(userId, listOf(fileUploadData))
                if (newAvatars.isEmpty()) return@transaction

                val newAvatarId = newAvatars[0].id

                val maxOrdinal = UserAvatars.slice(UserAvatars.ordinal.max())
                    .select { UserAvatars.user eq userId }
                    .firstOrNull()
                    ?.get(UserAvatars.ordinal.max()) ?: 0

                UserAvatars.insert {
                    it[user] = EntityID(userId, Users)
                    it[avatar] = EntityID(newAvatarId, Files)
                    it[ordinal] = maxOrdinal + 1
                }

                // Set as primary avatar
                UserEntity.findById(userId)?.apply {
                    primaryAvatar = EntityID(newAvatarId, Files)
                }
            }
        }
    }

    override fun uploadAvatar(userId: UUID, avatarUri: String) {
        val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex()
        val avatarIdString = uuidRegex.find(avatarUri)?.value ?: return
        val avatarId = try {
            UUID.fromString(avatarIdString)
        } catch (e: IllegalArgumentException) {
            return
        }

        transaction {
            val fileInfo = (Files leftJoin UserAvatars)
                .slice(Files.fileType, Files.extension, UserAvatars.user)
                .select { Files.id eq avatarId }
                .firstOrNull() ?: return@transaction

            val fileType = fileInfo[Files.fileType]
            val extension = fileInfo[Files.extension]
            val existingUserId = fileInfo[UserAvatars.user]?.value

            if (fileType == FileType.AVATAR && existingUserId != userId) {
                val maxOrdinal = UserAvatars.slice(UserAvatars.ordinal.max())
                    .select { UserAvatars.user eq userId }
                    .firstOrNull()
                    ?.get(UserAvatars.ordinal.max()) ?: 0

                UserAvatars.insert {
                    it[user] = EntityID(userId, Users)
                    it[avatar] = EntityID(avatarId, Files)
                    it[ordinal] = maxOrdinal + 1
                }

                val user = UserEntity.findById(userId)
                if (user?.primaryAvatar == null) {
                    user?.primaryAvatar = EntityID(avatarId, Files)
                }
            } else if (fileType != FileType.AVATAR) {
                val blogFile = BlogFile(avatarId, userId, extension, fileType)
                val file = storageService.getFile(blogFile)
                val fileUploadData = FileUploadData(file.name, file.inputStream())

                val newAvatars = storageService.storeAvatars(userId, listOf(fileUploadData))
                if (newAvatars.isEmpty()) return@transaction

                val newAvatarId = newAvatars[0].id

                val maxOrdinal = UserAvatars.slice(UserAvatars.ordinal.max())
                    .select { UserAvatars.user eq userId }
                    .firstOrNull()
                    ?.get(UserAvatars.ordinal.max()) ?: 0

                UserAvatars.insert {
                    it[user] = EntityID(userId, Users)
                    it[avatar] = EntityID(newAvatarId, Files)
                    it[ordinal] = maxOrdinal + 1
                }

                val user = UserEntity.findById(userId)
                if (user?.primaryAvatar == null) {
                    user?.primaryAvatar = EntityID(newAvatarId, Files)
                }
            }
        }
    }
}
