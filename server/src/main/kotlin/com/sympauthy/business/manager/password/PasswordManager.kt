package com.sympauthy.business.manager.password

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.repository.PasswordRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime.now
import java.util.UUID

@Singleton
class PasswordManager(
    @Inject private val hashGenerator: PasswordHashGenerator,
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val passwordRepository: PasswordRepository,
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
     * Create a new [password] for the [user].
     */
    suspend fun createPassword(user: User, password: String) {
        val hashConfig = uncheckedAdvancedConfig.orThrow().hashConfig
        validatePassword(password)

        val salt = randomGenerator.generate(hashConfig.saltLengthInBytes)
        val hashedPassword = hashGenerator.hash(password, salt)

        val entity = PasswordEntity(
            userId = user.id,
            salt = salt,
            hashedPassword = hashedPassword,
            creationDate = now(),
            expirationDate = null
        )
        passwordRepository.save(entity)
    }

    /**
     * A stored password is usable if it never expires or its expiration is still in the future.
     */
    private fun PasswordEntity.isUsable(): Boolean =
        expirationDate == null || expirationDate.isAfter(now())

    /**
     * Return true if the user identified by [userId] has any non-expired password stored — i.e. a password
     * credential that [arePasswordMatching] could match against. Used to offer the password method only when
     * the account actually has one (e.g. during re-authentication).
     */
    suspend fun hasPassword(userId: UUID): Boolean {
        return passwordRepository.findByUserId(userId).any { it.isUsable() }
    }

    /**
     * Return true if [password] matched against any non-expired one we have stored for the [user].
     */
    suspend fun arePasswordMatching(user: User, password: String): Boolean = coroutineScope {
        passwordRepository.findByUserId(user.id)
            .filter { it.isUsable() }
            .map { async { isPasswordMatching(it, password) } }
            .let { awaitAll(*it.toTypedArray()) }
            .any { it }
    }

    /**
     * Return true if the [password] matches the one in the [entity].
     *
     * To perform the test, the [password] is hashed using the salt of the password stored in the [entity].
     * Then the resulting hash and the hashed password stored in the [entity] are compared.
     */
    internal suspend fun isPasswordMatching(entity: PasswordEntity, password: String): Boolean {
        val hashPassword = hashGenerator.hash(password, entity.salt)
        return hashPassword.contentEquals(entity.hashedPassword)
    }
}
