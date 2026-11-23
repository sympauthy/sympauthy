package com.sympauthy.business.manager.password

import com.sympauthy.business.model.password.HashedPassword
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.platform.lock.LockKey
import com.sympauthy.platform.lock.LockManager
import com.sympauthy.platform.random.RandomGenerator
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime.now
import java.util.*

@Singleton
class PasswordManager(
    @Inject private val hashGenerator: PasswordHashGenerator,
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val passwordRepository: PasswordRepository,
    @Inject private val lockManager: LockManager,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {

    /**
     * Check if the [password] is valid against the rule defined in this authorization server configuration.
     * Throws otherwise.
     */
    fun validatePassword(password: String) {
        // To be implemented
    }

    /**
     * Create a new [password] for the [user], and return the identifier of the row holding it.
     */
    suspend fun createPassword(user: User, password: String): UUID = savePassword(user, hashPassword(password))

    /**
     * Hash [password] under a salt of its own, as it would be stored, without storing it.
     *
     * The password is held to the rules [validatePassword] applies, and its failure is thrown.
     */
    suspend fun hashPassword(password: String): HashedPassword {
        val hashConfig = uncheckedAdvancedConfig.orThrow().hashConfig
        validatePassword(password)

        val salt = randomGenerator.generate(hashConfig.saltLengthInBytes)
        val hashedPassword = hashGenerator.hash(password, salt, PasswordHashGenerator.CREATE)
        return HashedPassword(salt = salt, hash = hashedPassword)
    }

    /**
     * Make [newPassword] the only password the committed [user] signs in with, and return what was replaced: the
     * identifier of the row now holding [newPassword], and those of the passwords it replaced, which are deleted
     * whether they had expired or not.
     *
     * The passwords are read under the account's [LockKey.User], held until the caller's transaction ends, so two
     * replacements of one account are serialised: without it each reads the same passwords, deletes them, and
     * leaves its own beside the other's.
     */
    suspend fun replacePasswords(user: User, newPassword: HashedPassword): PasswordReplacement =
        lockManager.withLock(listOf(LockKey.User(user.id))) {
            val replacedIds = passwordRepository.findByUserId(user.id).mapNotNull(PasswordEntity::id)
            if (replacedIds.isNotEmpty()) {
                passwordRepository.deleteByIdIn(replacedIds)
            }
            PasswordReplacement(
                passwordId = savePassword(user, newPassword),
                replacedPasswordIds = replacedIds
            )
        }

    /**
     * Return true where the password identified by [passwordId] is still one of [user]'s, expired or not, and false
     * where it was replaced since.
     *
     * It is read under the account's [LockKey.User], held until the caller's transaction ends, so a replacement
     * either committed before it and is seen, or waits for that transaction and finds what it wrote.
     */
    suspend fun isPasswordStored(user: User, passwordId: UUID): Boolean =
        lockManager.withLock(listOf(LockKey.User(user.id))) {
            passwordRepository.findByUserId(user.id).any { it.id == passwordId }
        }

    private suspend fun savePassword(user: User, password: HashedPassword): UUID {
        val entity = PasswordEntity(
            userId = user.id,
            salt = password.salt,
            hashedPassword = password.hash,
            creationDate = now(),
            expirationDate = null,
            sessionId = user.sessionId
        )
        return passwordRepository.save(entity).id!!
    }

    /**
     * A stored password is usable if it never expires or its expiration is still in the future.
     */
    private fun PasswordEntity.isUsable(): Boolean =
        expirationDate == null || expirationDate.isAfter(now())

    /**
     * Return true if the [user] has any non-expired password stored — i.e. a password
     * credential that [findMatchingPasswordIdOrNull] could match against. Used to offer the password method only when
     * the account actually has one (e.g. during re-authentication).
     *
     * Where [includesExpired] is true, an expired password counts too: the account has a password to replace, if
     * none to sign in with.
     */
    suspend fun hasPassword(user: User, includesExpired: Boolean = false): Boolean {
        return passwordRepository.findByUserId(user.id).any { includesExpired || it.isUsable() }
    }

    /**
     * Return the identifier of the non-expired password stored for the [user] that [password] matches, or null
     * where it matches none.
     */
    suspend fun findMatchingPasswordIdOrNull(user: User, password: String): UUID? = coroutineScope {
        val usable = passwordRepository.findByUserId(user.id).filter { it.isUsable() }
        usable.map { async { isPasswordMatching(it, password) } }
            .let { awaitAll(*it.toTypedArray()) }
            .zip(usable)
            .firstOrNull { (matching, _) -> matching }
            ?.second
            ?.id
    }

    /**
     * Return true if the [password] matches the one in the [entity].
     *
     * To perform the test, the [password] is hashed using the salt of the password stored in the [entity].
     * Then the resulting hash and the hashed password stored in the [entity] are compared.
     */
    internal suspend fun isPasswordMatching(entity: PasswordEntity, password: String): Boolean {
        val hashPassword = hashGenerator.hash(password, entity.salt, PasswordHashGenerator.VERIFY)
        return hashPassword.contentEquals(entity.hashedPassword)
    }

    /**
     * What [replacePasswords] replaced: the identifier of the password now stored, [passwordId], and those of
     * the passwords it took the place of, [replacedPasswordIds].
     */
    class PasswordReplacement(
        val passwordId: UUID,
        val replacedPasswordIds: List<UUID>
    )
}
