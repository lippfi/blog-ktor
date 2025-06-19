package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.plugins.createJwtToken
import fi.lipp.blog.repository.*
import fi.lipp.blog.util.MessageLocalizer
import java.util.UUID
import fi.lipp.blog.service.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
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

    override fun getCurrentSessionInfo(userId: UUID): UserDto.SessionInfo {
        return transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            val userDiary = DiaryEntity.find { (Diaries.owner eq userId) and (Diaries.type eq DiaryType.PERSONAL) }.singleOrNull() ?: throw DiaryNotFoundException()

            UserDto.SessionInfo(
                login = userDiary.login,
                nickname = userEntity.nickname,
                language = userEntity.language,
                nsfw = userEntity.nsfw
            )
        }
    }

    @Throws(InviteCodeRequiredException::class, InvalidInviteCodeException::class, EmailIsBusyException::class, LoginIsBusyException::class, NicknameIsBusyException::class)
    override fun signUp(user: UserDto.Registration, inviteCode: String) {
        val inviteCodeEntity = transaction {
            if (inviteCode.trim().isEmpty()) {
                // TODO enable this later
//                if (Users.selectAll().count() > 1) throw InviteCodeRequiredException()
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

        // Check if email, login, or nickname is already in use by a registered user
        if (isEmailBusy(user.email)) throw EmailIsBusyException()
        if (isLoginBusy(user.login)) throw LoginIsBusyException()
        if (isNicknameBusy(user.nickname)) throw NicknameIsBusyException()

        // Create pending registration
        val pendingRegistrationId = transaction {
            PendingRegistrations.insertAndGetId {
                it[email] = user.email
                it[password] = encoder.encode(user.password)
                it[login] = user.login
                it[nickname] = user.nickname
                it[PendingRegistrations.inviteCode] = inviteCodeEntity?.id
                it[timezone] = timezoneParsed.id
                it[language] = user.language
            }
        }

        // Send confirmation email
        val subject = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_subject_registration_confirm",
            language = user.language
        )
        val body = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_body_registration_confirm",
            language = user.language,
            "confirmationCode" to pendingRegistrationId.value.toString()
        )
        mailService.sendEmail(
            subject = subject,
            text = body,
            recipient = user.email
        )
    }

    @Throws(ConfirmationCodeInvalidOrExpiredException::class)
    override fun confirmRegistration(confirmationCode: String): String {
        val pendingRegistration = transaction {
            val uuid = try {
                UUID.fromString(confirmationCode)
            } catch (e: Exception) {
                throw ConfirmationCodeInvalidOrExpiredException()
            }

            val pendingRegistration = PendingRegistrationEntity.findById(uuid)
                ?.takeIf { it.isValid }
                ?: throw ConfirmationCodeInvalidOrExpiredException()

            pendingRegistration
        }

        // Create the user from the pending registration
        val userId = transaction {
            // Check again if email, login, or nickname is already in use by a registered user
            // This is necessary in case someone registered with the same details
            // after the pending registration was created but before it was confirmed
            if (getUserByEmail(pendingRegistration.email) != null) throw EmailIsBusyException()
            if (getDiaryByLogin(pendingRegistration.login) != null) throw LoginIsBusyException()
            if (getUserByNickname(pendingRegistration.nickname) != null) throw NicknameIsBusyException()

            val userId = Users.insertAndGetId {
                it[email] = pendingRegistration.email
                it[password] = pendingRegistration.password // Already encoded
                it[nickname] = pendingRegistration.nickname
                it[inviteCode] = pendingRegistration.inviteCode

                it[sex] = Sex.UNDEFINED
                it[nsfw] = NSFWPolicy.HIDE
                it[timezone] = pendingRegistration.timezone
                it[language] = pendingRegistration.language
            }

            Diaries.insert {
                it[name] = "Unnamed blog"
                it[subtitle] = ""
                it[login] = pendingRegistration.login
                it[owner] = userId
                it[type] = DiaryType.PERSONAL
                it[defaultReadGroup] = accessGroupService.everyoneGroupUUID
                it[defaultCommentGroup] = accessGroupService.registeredGroupUUID
                it[defaultReactGroup] = accessGroupService.friendsGroupUUID
            }

            NotificationSettingsEntity.new {
                user = UserEntity.findById(userId)!!
                notifyAboutComments = true
                notifyAboutReplies = true
                notifyAboutPostReactions = true
                notifyAboutCommentReactions = true
                notifyAboutPrivateMessages = true
                notifyAboutMentions = true
            }

            // Delete the pending registration
            pendingRegistration.delete()

            userId.value
        }

        // Return JWT token for the new user
        return createJwtToken(userId)
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

    override fun updateEmail(userId: UUID, email: String) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            if (userEntity.email != email && isEmailBusy(email)) throw EmailIsBusyException()

            // Create pending email change
            val pendingEmailChangeId = PendingEmailChanges.insertAndGetId {
                it[user] = userEntity.id
                it[newEmail] = email
            }

            // Send confirmation email
            val subject = MessageLocalizer.getLocalizedMessage(
                messageKey = "email_subject_email_change_confirm",
                language = userEntity.language
            )
            val body = MessageLocalizer.getLocalizedMessage(
                messageKey = "email_body_email_change_confirm",
                language = userEntity.language,
                "confirmationCode" to pendingEmailChangeId.toString()
            )
            mailService.sendEmail(
                subject = subject,
                text = body,
                recipient = email
            )
        }
    }

    override fun confirmEmailUpdate(confirmationCode: String) {
        transaction {
            val uuid = try {
                UUID.fromString(confirmationCode)
            } catch (e: Exception) {
                throw ConfirmationCodeInvalidOrExpiredException()
            }

            val pendingEmailChange = PendingEmailChangeEntity.findById(uuid)
                ?.takeIf { it.isValid }
                ?: throw ConfirmationCodeInvalidOrExpiredException()

            val userEntity = pendingEmailChange.user

            // Check again if email is already in use
            // This is necessary in case someone registered with the same email
            // after the pending email change was created but before it was confirmed
            if (userEntity.email != pendingEmailChange.newEmail && isEmailBusy(pendingEmailChange.newEmail)) {
                throw EmailIsBusyException()
            }

            // Store old email for notification
            val oldEmail = userEntity.email

            // Update the user's email
            userEntity.email = pendingEmailChange.newEmail

            // Delete the pending email change
            pendingEmailChange.delete()

            // Send confirmation email to old address
            val subject = MessageLocalizer.getLocalizedMessage(
                messageKey = "email_subject_email_changed",
                language = userEntity.language
            )
            val body = MessageLocalizer.getLocalizedMessage(
                messageKey = "email_body_email_changed",
                language = userEntity.language,
                "newEmail" to pendingEmailChange.newEmail
            )
            mailService.sendEmail(
                subject = subject,
                text = body,
                recipient = oldEmail
            )
        }
    }

    override fun updateNickname(userId: UUID, nickname: String) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            if (userEntity.nickname != nickname && isNicknameBusy(nickname)) throw NicknameIsBusyException()
            userEntity.apply {
                this.nickname = nickname
            }
        }
    }

    override fun updatePassword(userId: UUID, newPassword: String, oldPassword: String) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            if (!encoder.matches(oldPassword, userEntity.password)) throw WrongPasswordException()
            userEntity.apply {
                password = encoder.encode(newPassword)
            }
        }
    }

    override fun updateSex(userId: UUID, sex: Sex) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                this.sex = sex
            }
        }
    }

    override fun updateTimezone(userId: UUID, timezone: String) {
        val timezoneParsed = try {
            kotlinx.datetime.TimeZone.of(timezone)
        } catch (e: Exception) {
            throw InvalidTimezoneException()
        }
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                this.timezone = timezoneParsed.id
            }
        }
    }

    override fun updateLanguage(userId: UUID, language: Language) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                this.language = language
            }
        }
    }

    override fun updateNSFWPolicy(userId: UUID, nsfw: NSFWPolicy) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                this.nsfw = nsfw
            }
        }
    }

    override fun updateBirthDate(userId: UUID, birthDate: LocalDate) {
        transaction {
            val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
            userEntity.apply {
                this.birthdate = birthDate
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

        val subject = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_subject_password_reset",
            language = userEntity.language
        )
        val body = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_body_password_reset",
            language = userEntity.language,
            "resetCode" to resetCode.toString()
        )
        mailService.sendEmail(
            subject = subject,
            text = body,
            recipient = userEntity.email
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
        val subject = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_subject_password_changed",
            language = userEntity.language
        )
        val body = MessageLocalizer.getLocalizedMessage(
            messageKey = "email_body_password_changed",
            language = userEntity.language
        )
        mailService.sendEmail(
            subject = subject,
            text = body,
            recipient = userEntity.email
        )
    }

    override fun isEmailBusy(email: String): Boolean = getUserByEmail(email) != null || isEmailReserved(email)
    override fun isLoginBusy(login: String): Boolean = getDiaryByLogin(login) != null || isLoginReserved(login)
    override fun isNicknameBusy(nickname: String): Boolean = getUserByNickname(nickname) != null || isNicknameReserved(nickname)

    private fun isEmailReserved(email: String): Boolean = getPendingRegistrationByEmail(email) != null
    private fun isLoginReserved(login: String): Boolean = getPendingRegistrationByLogin(login) != null
    private fun isNicknameReserved(nickname: String): Boolean = getPendingRegistrationByNickname(nickname) != null

    private fun getPendingRegistrationByEmail(email: String): PendingRegistrationEntity? {
        return transaction { 
            PendingRegistrationEntity.find { PendingRegistrations.email eq email }
                .filter { it.isValid }
                .firstOrNull() 
        }
    }

    private fun getPendingRegistrationByLogin(login: String): PendingRegistrationEntity? {
        return transaction { 
            PendingRegistrationEntity.find { PendingRegistrations.login eq login }
                .filter { it.isValid }
                .firstOrNull() 
        }
    }

    private fun getPendingRegistrationByNickname(nickname: String): PendingRegistrationEntity? {
        return transaction { 
            PendingRegistrationEntity.find { PendingRegistrations.nickname eq nickname }
                .filter { it.isValid }
                .firstOrNull() 
        }
    }

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
            val userEntity = UserEntity.findById(userId)!!
            userEntity.primaryAvatar = permutation.firstOrNull()?.let { EntityID(it, Files) }
        }
    }

    override fun addAvatar(userId: UUID, files: List<FileUploadData>): List<String> {
        val existingMaxOrdinal = transaction {
            UserAvatars.slice(UserAvatars.ordinal.max())
                .select { UserAvatars.user eq userId }
                .firstOrNull()
                ?.get(UserAvatars.ordinal.max()) ?: 0
        }

        // TODO FileType.AVATAR where?
        val newAvatars = storageService.storeAvatars(userId, files)

        transaction {
            val user = UserEntity.findById(userId) ?: return@transaction
            val needsSetPrimary = user.primaryAvatar == null

            newAvatars.forEachIndexed { index, avatarFile ->
                UserAvatars.insert {
                    it[UserAvatars.user] = EntityID(userId, Users)
                    it[avatar] = EntityID(avatarFile.id, Files)
                    it[UserAvatars.ordinal] = existingMaxOrdinal + index + 1
                }

                if (needsSetPrimary && index == 0) {
                    user.primaryAvatar = EntityID(avatarFile.id, Files)
                }
            }
        }

        return newAvatars.map { storageService.getFileURL(it) }
    }

    // TODO remove duplication
    override fun addAvatar(userId: UUID, avatarUri: String) {
        uploadAvatar(userId, avatarUri)
    }

    override fun deleteAvatar(userId: UUID, avatarUri: String) {
        transaction {
            val user = UserEntity.findById(userId) ?: return@transaction
            val avatarEntity = getFileEntityByUri(avatarUri)
            val avatarId = avatarEntity.id.value

            UserAvatars.deleteWhere {
                (UserAvatars.user eq EntityID(userId, Users)) and 
                (avatar eq EntityID(avatarId, Files))
            }

            if (user.primaryAvatar?.value == avatarId) {
                val firstAvatar = UserAvatars
                    .slice(UserAvatars.avatar)
                    .select { UserAvatars.user eq EntityID(userId, Users) }
                    .orderBy(UserAvatars.ordinal)
                    .firstOrNull()
                    ?.get(UserAvatars.avatar)

                user.primaryAvatar = firstAvatar
            }
        }
    }

    override fun updateNotificationSettings(userId: UUID, settings: fi.lipp.blog.data.NotificationSettings) {
        transaction {
            val notificationSettingsEntity = NotificationSettingsEntity.find {
                fi.lipp.blog.repository.NotificationSettings.user eq userId 
            }.single()

            notificationSettingsEntity.apply {
                notifyAboutComments = settings.notifyAboutComments
                notifyAboutReplies = settings.notifyAboutReplies
                notifyAboutPostReactions = settings.notifyAboutPostReactions
                notifyAboutCommentReactions = settings.notifyAboutCommentReactions
                notifyAboutPrivateMessages = settings.notifyAboutPrivateMessages
                notifyAboutMentions = settings.notifyAboutMentions
            }
        }
    }

    override fun search(text: String): List<UserDto.View> {
        return transaction {
            val searchPattern = "%${text.trim()}%"

            (Users innerJoin Diaries)
                .slice(Users.nickname, Diaries.login, Users.primaryAvatar)
                .select {
                    (Users.nickname like searchPattern or (Diaries.login like searchPattern)) and
                    (Diaries.type eq DiaryType.PERSONAL) and
                    (Diaries.owner eq Users.id) and
                    (Diaries.login neq "system")
                }
                .orderBy(Diaries.login)
                .limit(10)
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

    override fun getByLogins(logins: List<String>): List<UserDto.View> {
        if (logins.isEmpty()) {
            return emptyList()
        }

        return transaction {
            (Users innerJoin Diaries)
                .slice(Users.nickname, Diaries.login, Users.primaryAvatar)
                .select {
                    (Diaries.login inList logins) and
                    (Diaries.type eq DiaryType.PERSONAL) and
                    (Diaries.owner eq Users.id)
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

            FriendshipEntity.new {
                user1 = request.fromUser
                user2 = request.toUser
                createdAt = LocalDateTime.now().toKotlinLocalDateTime()
            }

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

            FriendLabelEntity.find {
                (FriendLabels.user eq userId and (FriendLabels.friend eq friend.id)) or
                (FriendLabels.user eq friend.id and (FriendLabels.friend eq userId))
            }.forEach { it.delete() }
        }
    }

    override fun updateFriendLabel(userId: UUID, friendLogin: String, label: String?) {
        transaction {
            val friend = getUserByLogin(friendLogin) ?: throw UserNotFoundException()

            FriendshipEntity.find {
                (Friends.user1 eq userId and (Friends.user2 eq friend.id)) or
                (Friends.user1 eq friend.id and (Friends.user2 eq userId))
            }.firstOrNull() ?: throw NotFriendsException()

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

    override fun changePrimaryAvatar(userId: UUID, avatarUri: String) {
        transaction {
            val avatarEntity = getFileEntityByUri(avatarUri)
            val avatarId = avatarEntity.id.value
            val avatarInUserCollection = UserAvatars.select {
                (UserAvatars.user eq userId) and (UserAvatars.avatar eq avatarId)
            }.count() > 0

            if (avatarInUserCollection) {
                UserEntity.findById(userId)?.apply {
                    primaryAvatar = avatarEntity.id
                }
            } else {
                reuploadFileAsUserAvatar(userId, avatarId)
            }
        }
    }

    // TODO is it different from add?
    override fun uploadAvatar(userId: UUID, avatarUri: String) {
        transaction {
            val avatarEntity = getFileEntityByUri(avatarUri)
            val avatarId = avatarEntity.id.value

            val avatarInUserCollection = UserAvatars.select {
                (UserAvatars.user eq userId) and (UserAvatars.avatar eq avatarId)
            }.count() > 0

            if (avatarInUserCollection) {
                return@transaction
            }
            reuploadFileAsUserAvatar(userId, avatarId)
        }
    }

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.getFileEntityByUri(uri: String): FileEntity {
        val fileName = Regex("""([^/]+)/?$""").find(uri)?.groupValues?.get(1) ?: throw InvalidAvatarUriException()
        return FileEntity.find { Files.name eq fileName }.firstOrNull() ?: throw InvalidAvatarUriException()
    }

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.reuploadFileAsUserAvatar(userId: UUID, avatarId: UUID) {
        val fileEntity = FileEntity.findById(avatarId) ?: return
        val file = storageService.getFile(fileEntity.toBlogFile())
        val fileUploadData = FileUploadData(file.name, file.inputStream())

        val newAvatars = storageService.storeAvatars(userId, listOf(fileUploadData))
        if (newAvatars.isEmpty()) return

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

    override fun followUser(userId: UUID, targetLogin: String) {
        transaction {
            val targetUser = getUserByLogin(targetLogin) ?: throw UserNotFoundException()

            val alreadyFollowing = UserFollows
                .select { (UserFollows.follower eq userId) and (UserFollows.following eq targetUser.id) }
                .count() > 0

            if (alreadyFollowing) {
                throw AlreadyFollowingException()
            }

            UserFollowEntity.new {
                follower = UserEntity[userId]
                following = targetUser
            }
        }
    }

    override fun unfollowUser(userId: UUID, targetLogin: String) {
        transaction {
            val targetUser = getUserByLogin(targetLogin) ?: throw UserNotFoundException()

            val followEntity = UserFollowEntity.find {
                (UserFollows.follower eq userId) and (UserFollows.following eq targetUser.id)
            }.firstOrNull() ?: throw NotFollowingException()

            followEntity.delete()
        }
    }

    override fun getFollowing(userId: UUID): List<UserDto.View> {
        return transaction {
            val followingUsers = UserFollowEntity.find { UserFollows.follower eq userId }
                .map { it.following.id.value }

            if (followingUsers.isEmpty()) {
                return@transaction emptyList()
            }

            (Users innerJoin Diaries)
                .slice(Users.nickname, Diaries.login, Users.primaryAvatar)
                .select {
                    (Users.id inList followingUsers) and
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

    override fun getFollowers(userId: UUID): List<UserDto.View> {
        return transaction {
            val followerUsers = UserFollowEntity.find { UserFollows.following eq userId }
                .map { it.follower.id.value }

            if (followerUsers.isEmpty()) {
                return@transaction emptyList()
            }

            (Users innerJoin Diaries)
                .slice(Users.nickname, Diaries.login, Users.primaryAvatar)
                .select {
                    (Users.id inList followerUsers) and
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

    // System user ID for creating system resources
    private val systemUserId = UUID.fromString("00000000-0000-0000-0000-000000000000")

    override fun getOrCreateSystemUser(): UUID {
        val uuid = getUserByLogin("system")?.id?.value
        if (uuid != null) return uuid

        val systemUser = UserDto.Registration(
            login = "system",
            email = "system@example.com",
            password = UUID.randomUUID().toString(),
            nickname = "system",
            timezone = "Asia/Nicosia",
            language = Language.EN,
        )

        // Create the system user directly, bypassing the 2-step registration process
        val timezoneParsed = kotlinx.datetime.TimeZone.of(systemUser.timezone)
        val userId = transaction {
            val userId = Users.insertAndGetId {
                it[email] = systemUser.email
                it[password] = encoder.encode(systemUser.password)
                it[nickname] = systemUser.nickname
                it[inviteCode] = null

                it[sex] = Sex.UNDEFINED
                it[nsfw] = NSFWPolicy.HIDE
                it[timezone] = timezoneParsed.id
                it[language] = systemUser.language
            }

            Diaries.insert {
                it[name] = "Unnamed blog"
                it[subtitle] = ""
                it[login] = systemUser.login
                it[owner] = userId
                it[type] = DiaryType.PERSONAL
                it[defaultReadGroup] = accessGroupService.everyoneGroupUUID
                it[defaultCommentGroup] = accessGroupService.registeredGroupUUID
                it[defaultReactGroup] = accessGroupService.friendsGroupUUID
            }

            userId.value
        }

        return userId
    }

    override fun getUserLanguage(userId: UUID): Language? {
        return transaction {
            UserEntity.findById(userId)?.language
        }
    }
}
