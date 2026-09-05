package com.sympauthy.business.model.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.util.Base64URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class HashAlgorithmTest {

    private val exampleAccessToken = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"

    /** OpenID Connect Core 1.0, appendix A.3. */
    @Test
    fun `atHash - Hash an access token as the OpenID Connect Core example does`() {
        assertEquals("77QmUPtjPfzWtF2AnpK9RQ", HashAlgorithm.SHA256.atHash(exampleAccessToken))
    }

    @ParameterizedTest
    @EnumSource(HashAlgorithm::class)
    fun `atHash - Keep the left half of the digest`(hashAlgorithm: HashAlgorithm) {
        val digestSizeInBytes = when (hashAlgorithm) {
            HashAlgorithm.SHA256 -> 32
            HashAlgorithm.SHA384 -> 48
            HashAlgorithm.SHA512 -> 64
        }

        val hash = Base64URL(hashAlgorithm.atHash(exampleAccessToken)).decode()

        assertEquals(digestSizeInBytes / 2, hash.size)
    }

    @ParameterizedTest
    @EnumSource(JwtAlgorithm::class)
    fun `Every JwtAlgorithm hashes an access token the way its own name is read inbound`(
        jwtAlgorithm: JwtAlgorithm
    ) {
        val inbound = HashAlgorithm.ofOrNull(JWSAlgorithm.parse(jwtAlgorithm.name))

        assertEquals(jwtAlgorithm.hashAlgorithm, inbound)
    }

    @Test
    fun `ofOrNull - Read the digest off the size the algorithm is named for`() {
        assertEquals(HashAlgorithm.SHA256, HashAlgorithm.ofOrNull(JWSAlgorithm.ES256))
        assertEquals(HashAlgorithm.SHA384, HashAlgorithm.ofOrNull(JWSAlgorithm.PS384))
        assertEquals(HashAlgorithm.SHA512, HashAlgorithm.ofOrNull(JWSAlgorithm.RS512))
    }

    @Test
    fun `ofOrNull - Return null for an algorithm naming no size`() {
        assertNull(HashAlgorithm.ofOrNull(JWSAlgorithm.parse("EdDSA")))
    }
}
