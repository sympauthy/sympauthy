package com.sympauthy.business.manager.invitation

import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.server.Computation
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.SCrypt
import java.security.MessageDigest
import java.util.concurrent.ExecutorService

/**
 * Handles hashing of invitation tokens for secure storage.
 *
 * Uses a two-level strategy:
 * - **Lookup hash**: unsalted SHA-256 of the raw token, stored in a unique-indexed column for O(1) database lookup.
 * - **Verification hash**: salted scrypt hash for cryptographic verification after lookup.
 */
@Singleton
class InvitationHashGenerator(
    @Inject @param:Computation private val executorService: ExecutorService,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()

    private val hashConfig: HashConfig
        get() = uncheckedAdvancedConfig.orThrow().invitationConfig.hashConfig

    /**
     * Compute a SHA-256 hash of [token] for fast database lookup.
     * This hash is unsalted and deterministic so it can be used as a unique index.
     */
    fun computeLookupHash(token: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(token)
    }

    /**
     * Compute a salted scrypt hash of [token] for secure verification.
     */
    suspend fun hash(token: ByteArray, salt: ByteArray): ByteArray = withContext(coroutineDispatcher) {
        val config = hashConfig
        SCrypt.generate(
            token,
            salt,
            config.costParameter,
            config.blockSize,
            config.parallelizationParameter,
            config.keyLengthInBytes
        )
    }

    /**
     * Verify that [token] matches the stored [hashedToken] using the given [salt].
     */
    suspend fun verify(token: ByteArray, salt: ByteArray, hashedToken: ByteArray): Boolean {
        val computed = hash(token, salt)
        return computed.contentEquals(hashedToken)
    }
}
