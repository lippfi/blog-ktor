package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.plugins.createJwtToken
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.MailService
import fi.lipp.blog.service.PasswordEncoder
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.UserService
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.time.LocalDateTime
import java.util.*

class UserServiceImpl(private val encoder: PasswordEncoder, private val mailService: MailService, private val storageService: StorageService) : UserService {
    override fun generateInviteCode(userId: UUID): String {
        val inviteCode = transaction {
            InviteCodes.insertAndGetId {
                it[creator] = userId
                it[issuedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            }
        }
        return inviteCode.value.toString()
    }

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

        if (isEmailBusy(user.email)) throw EmailIsBusyException()
        if (isLoginBusy(user.login)) throw LoginIsBusyException()
        if (isNicknameBusy(user.nickname)) throw NicknameIsBusyException()
        transaction {
            val userId = Users.insertAndGetId {
                it[email] = user.email
                it[password] = encoder.encode(user.password)
                it[nickname] = user.nickname
                it[Users.inviteCode] = inviteCodeEntity?.id
            }
            Diaries.insert {
                it[name] = "Unnamed blog"
                it[login] = user.login
                it[owner] = userId
                it[type] = DiaryType.PERSONAL
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

    override fun getUserInfo(login: String): UserDto.ProfileInfo {
        return transaction { 
            val diaryEntity = DiaryEntity.find { Diaries.login eq login }.singleOrNull() ?: throw DiaryNotFoundException()
            val userEntity = UserEntity.findById(diaryEntity.owner) ?: throw UserNotFoundException()
            UserDto.ProfileInfo(
                login = diaryEntity.login,
                email = userEntity.email,
                nickname = userEntity.nickname,
                registrationTime = userEntity.registrationTime
            )
        }
    }

    override fun update(userId: UUID, user: UserDto.Registration, oldPassword: String) {
        val userEntity = UserEntity.findById(userId) ?: throw UserNotFoundException()
        if (!encoder.matches(oldPassword, userEntity.password)) throw WrongPasswordException()

        if (userEntity.email != user.email && isEmailBusy(user.email)) throw EmailIsBusyException()
        if (userEntity.nickname != user.nickname && isNicknameBusy(user.nickname)) throw NicknameIsBusyException()

        val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
        if (diaryEntity.login != user.login && isLoginBusy(user.login)) throw LoginIsBusyException()

        transaction {
            userEntity.apply {
                email = user.email
                password = encoder.encode(user.password)
                nickname = user.nickname
            }
            diaryEntity.apply {
                login = user.login
            }
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
            newAvatars.forEachIndexed { index, avatarFile ->
                UserAvatars.insert {
                    it[user] = userId
                    it[avatar] = avatarFile.id
                    it[ordinal] = existingMaxOrdinal + index + 1
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
            UserAvatars.deleteWhere { (user eq userId) and (avatar eq avatarId) }
        }
    }
}