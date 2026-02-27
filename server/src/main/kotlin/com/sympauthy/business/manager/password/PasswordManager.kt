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
        // TODO:
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
     * Return true if [password] matched against any non-expired one we have stored for the [user].
     */
    suspend fun arePasswordMatching(user: User, password: String): Boolean = coroutineScope {
        passwordRepository.findByUserId(user.id)
            .filter { it.expirationDate == null || it.expirationDate.isBefore(now()) }
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
