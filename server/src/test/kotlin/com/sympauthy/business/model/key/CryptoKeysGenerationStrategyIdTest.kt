package com.sympauthy.business.model.key

import com.sympauthy.business.manager.jwt.CryptoKeysGenerationStrategy
import com.sympauthy.util.configName
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * Holds the strategies a deployment may configure to the implementations published for them.
 *
 * A value naming no bean and a bean published under a name no value spells both fail late: the
 * configuration parses, the server starts, and the deployment learns of it the first time a key is
 * generated, under an error code saying only that the server is missing a strategy.
 */
@MicronautTest(environments = ["default", "test", "h2"])
class CryptoKeysGenerationStrategyIdTest {

    /**
     * The map [com.sympauthy.business.manager.key.CryptoKeysManager] resolves a strategy out of.
     */
    @Inject
    lateinit var generationStrategies: Map<String, CryptoKeysGenerationStrategy>

    @Test
    fun `Every strategy is published under the name its entry is configured with`() {
        val qualifiers = CryptoKeysGenerationStrategyQualifiers::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }

        assertEquals(
            CryptoKeysGenerationStrategyId.entries.map { it.configName }.sorted(),
            qualifiers.sorted()
        )
    }

    @Test
    fun `Every strategy has an implementation, and every implementation a strategy`() {
        assertEquals(
            CryptoKeysGenerationStrategyId.entries.map { it.configName }.sorted(),
            generationStrategies.keys.sorted()
        )
    }
}
