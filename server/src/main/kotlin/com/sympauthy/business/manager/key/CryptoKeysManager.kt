package com.sympauthy.business.manager.key

import com.sympauthy.business.mapper.CryptoKeysMapper
import com.sympauthy.business.model.key.CryptoKeys
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.key.KeyAlgorithm
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.repository.CryptoKeysRepository
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import com.sympauthy.monitoring.telemetry.traceCoroutineContext
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.rx3.rxSingle
import java.util.concurrent.ConcurrentHashMap

@Singleton
class CryptoKeysManager(
    @Inject private val keysRepository: CryptoKeysRepository,
    @Inject private val keysMapper: CryptoKeysMapper,
    @Inject private val advancedConfig: AdvancedConfig,
    /**
     * Every generation strategy the container publishes, by the qualifier naming it — which is the
     * set of words `advanced.keys-generation-strategy` accepts.
     */
    @Inject private val generationStrategies: Map<String, CryptoKeysGenerationStrategy>,
    @Inject private val customSpanTracer: CustomSpanTracer
) {
    /**
     * The keys read since the server started, which a request is answered from without waiting.
     */
    private val heldKeys = ConcurrentHashMap<String, CryptoKeys>()

    /**
     * The read of each key some request started, which every request waiting for that key awaits.
     */
    private val keysMap = mutableMapOf<String, Single<CryptoKeys>>()

    /**
     * The key [name] names, read once and held from then on, and generated with [algorithm] where it does not
     * exist yet.
     *
     * A request finding the key held is answered at once; one that waits for it, reading it or waiting for another
     * request already reading it, is traced as waiting.
     */
    internal suspend fun getKey(name: String, algorithm: KeyAlgorithm): CryptoKeys {
        heldKeys[name]?.let { return it }
        return customSpanTracer.loadSigningKey(name) {
            readingKey(name, algorithm).await().also { heldKeys[name] = it }
        }
    }

    private fun readingKey(name: String, algorithm: KeyAlgorithm): Single<CryptoKeys> {
        return synchronized(keysMap) {
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
    }

    internal fun getOrGenerateKey(name: String, algorithm: KeyAlgorithm): Single<CryptoKeys> {
        return getStoredKey(name, algorithm)
            .switchIfEmpty(generateKey(name, algorithm))
    }

    internal fun getStoredKey(name: String, algorithm: KeyAlgorithm): Maybe<CryptoKeys> {
        return rxMaybe(traceCoroutineContext()) {
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
        return rxSingle(traceCoroutineContext()) {
            customSpanTracer.generateSigningKey(name) {
                advancedConfig.orThrow().keysGenerationStrategy
                    .resolve(generationStrategies)
                    .generateKeys(name, algorithm)
            }
        }
    }
}
