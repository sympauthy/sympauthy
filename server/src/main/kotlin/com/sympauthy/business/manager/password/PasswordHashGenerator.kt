package com.sympauthy.business.manager.password

import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.server.Computation
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.generators.SCrypt
import java.util.concurrent.ExecutorService

/**
 * Manager in charge of salting and hashing the password.
 */
@Singleton
class PasswordHashGenerator(
    @Inject @param:Computation private val executorService: ExecutorService,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
    @Inject private val authenticationMetrics: AuthenticationMetrics
) {
    /**
     * As the hashing is quite slow, we isolate them into their own dispatcher to avoid blocking the
     * main dispatchers handling the requests.
     */
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()

    /**
     * Hash [password] with [salt], recorded as a hash for [operation]: [CREATE] or [VERIFY].
     */
    suspend fun hash(password: String, salt: ByteArray, operation: String): ByteArray =
        withContext(coroutineDispatcher) {
            authenticationMetrics.measureHash(operation) {
                val config = uncheckedAdvancedConfig.orThrow().hashConfig
                SCrypt.generate(
                    password.toByteArray(Charsets.UTF_16),
                    salt,
                    config.costParameter,
                    config.blockSize,
                    config.parallelizationParameter,
                    config.keyLengthInBytes
                )
            }
        }

    companion object {
        /** A hash of a password being set. */
        const val CREATE = "create"

        /** A hash of a password being checked against one stored. */
        const val VERIFY = "verify"
    }
}
