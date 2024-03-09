package com.sympauthy.business.manager.key

import com.sympauthy.business.mapper.CryptoKeysMapper
import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.business.model.key.KeyAlgorithm
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.repository.CryptoKeysRepository
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.rx3.rxSingle

@Singleton
class CryptoKeysManager(
    @Inject private val keysRepository: CryptoKeysRepository,
    @Inject private val keysMapper: CryptoKeysMapper,
    @Inject private val advancedConfig: AdvancedConfig
) {
    private val keysMap = mutableMapOf<String, Single<CryptoKeys>>()

    internal suspend fun getKey(name: String, algorithm: KeyAlgorithm): CryptoKeys {
        // Get key and return if found.
        val existingKey = keysMap[name]?.await()
        if (existingKey != null) {
            return existingKey
        }

        val key = synchronized(keysMap) {
            // Synchronized check to see if it has not been created by another worker thread.
            val existingKeySingle = keysMap[name]
            if (existingKeySingle != null) {
                existingKeySingle
            } else {
                // Otherwise create the single that will generate the key.
                // Cache the key and store it into the map to be reused.
                val newKeySingle = getOrGenerateKey(name, algorithm).cache()
                keysMap[name] = newKeySingle
                newKeySingle
            }
        }
        return key.await()
    }

    internal fun getOrGenerateKey(name: String, algorithm: KeyAlgorithm): Single<CryptoKeys> {
        return getStoredKey(name, algorithm)
            .switchIfEmpty(generateKey(name, algorithm))
    }

    internal fun getStoredKey(name: String, algorithm: KeyAlgorithm): Maybe<CryptoKeys> {
        return rxMaybe {
            val existingKey = keysRepository.findByName(name)
            if (existingKey != null) {
                if (existingKey.algorithm != algorithm.name) {
                    keysRepository.delete(existingKey)
                    null
                } else existingKey
            } else null
        }.map(keysMapper::toCryptoKeys)
    }

    internal fun generateKey(name: String, algorithm: KeyAlgorithm): Single<CryptoKeys> {
        return rxSingle {
            advancedConfig.orThrow().keysGenerationStrategy.generateKeys(name, algorithm)
        }
    }
}
