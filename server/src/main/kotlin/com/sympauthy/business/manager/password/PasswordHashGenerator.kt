package com.sympauthy.business.manager.password

import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
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
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {
    /**
     * As the hashing is quite slow, we isolate them into their own dispatcher to avoid blocking the
     * main dispatchers handling the requests.
     */
    private val coroutineDispatcher = executorService.asCoroutineDispatcher()

    suspend fun hash(password: String, salt: ByteArray): ByteArray = withContext(coroutineDispatcher) {
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
