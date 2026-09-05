package com.sympauthy.business.manager.provider.openidconnect

import com.nimbusds.jose.JWSAlgorithm
import com.sympauthy.business.exception.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ProviderIdTokenManagerTest {

    private val manager = ProviderIdTokenManager()

    /** OpenID Connect Core 1.0, appendix A.3. */
    private val accessToken = "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"
    private val atHash = "77QmUPtjPfzWtF2AnpK9RQ"

    @Test
    fun `validateAtHash - Accept the hash of the access token from the same response`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, accessToken, JWSAlgorithm.RS256, "provider")
        }
    }

    @Test
    fun `validateAtHash - Refuse the hash of another access token`() {
        val exception = assertThrows<BusinessException> {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.RS256, "provider")
        }

        assertEquals("provider.openid_connect.invalid_at_hash", exception.detailsId)
    }

    @Test
    fun `validateAtHash - Leave the claim unchecked under an algorithm naming no digest`() {
        assertDoesNotThrow {
            manager.validateAtHash(atHash, "another-access-token", JWSAlgorithm.parse("EdDSA"), "provider")
        }
    }
}
