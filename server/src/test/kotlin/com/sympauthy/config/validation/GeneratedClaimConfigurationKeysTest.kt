package com.sympauthy.config.validation

import com.sympauthy.config.DeclaredConfigurationKey
import com.sympauthy.config.DeclaredConfigurationKeyReader
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.validation.ClaimsConfigValidator.Companion.KEYS_NO_GENERATED_CLAIM_READS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds every key a claim's configuration declares to the set a generated claim refuses, which is all
 * of them.
 *
 * A key missing from that set is one `claims.sub` accepts and nothing acts on, which is the state
 * `published-in` shipped in — the server starts, reports itself ready, and the value decides nothing.
 * The keys come off the bean definitions the annotation processor wrote, so the set is closed by
 * construction and the next property added to a claim has to be answered for before the build passes.
 */
class GeneratedClaimConfigurationKeysTest {

    @Test
    fun `Every key a claim declares is refused on a generated claim`() {
        val reader = DeclaredConfigurationKeyReader()
        val prefix = "$CLAIMS_KEY.*."
        val declared = reader.serverBeanDefinitions()
            .flatMap(reader::keysOf)
            .map(DeclaredConfigurationKey::pattern)
            .filter { it.startsWith(prefix) }
            .mapTo(mutableSetOf()) { it.removePrefix(prefix) }

        // A key another key is written under — `acl` — carries no value of its own, and what is written
        // under it is refused key by key.
        val values = declared.filterNot { key -> declared.any { it.startsWith("$key.") } }.toSet()

        assertEquals(
            values, KEYS_NO_GENERATED_CLAIM_READS.keys,
            "These keys are what a claim's configuration accepts and what a generated claim refuses, " +
                "and they have to be the same set. A key missing from the second is accepted on " +
                "claims.sub and read by nothing; a key missing from the first is refused under a name " +
                "no longer written anywhere."
        )
    }
}
