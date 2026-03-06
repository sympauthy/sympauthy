package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest
import java.util.*

class PkceManagerTest {

    private val pkceManager = PkceManager()

    private fun computeS256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    @Test
    fun `verifyCodeVerifier - Succeeds with valid S256 verifier`() {
        val codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val codeChallenge = computeS256Challenge(codeVerifier)

        assertDoesNotThrow {
            pkceManager.verifyCodeVerifier(codeVerifier, codeChallenge, CodeChallengeMethod.S256)
        }
    }

    @Test
    fun `verifyCodeVerifier - Fails with wrong verifier`() {
        val codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val codeChallenge = computeS256Challenge(codeVerifier)

        val exception = assertThrows<BusinessException> {
            pkceManager.verifyCodeVerifier("wrong-verifier", codeChallenge, CodeChallengeMethod.S256)
        }
        assertEquals("token.pkce.invalid_code_verifier", exception.detailsId)
    }

    @Test
    fun `verifyCodeVerifier - Fails when verifier is missing but challenge was stored`() {
        val codeChallenge = computeS256Challenge("some-verifier")

        val exception = assertThrows<BusinessException> {
            pkceManager.verifyCodeVerifier(null, codeChallenge, CodeChallengeMethod.S256)
        }
        assertEquals("token.pkce.missing_code_verifier", exception.detailsId)
    }

    @Test
    fun `verifyCodeVerifier - Fails when verifier is blank but challenge was stored`() {
        val codeChallenge = computeS256Challenge("some-verifier")

        val exception = assertThrows<BusinessException> {
            pkceManager.verifyCodeVerifier("", codeChallenge, CodeChallengeMethod.S256)
        }
        assertEquals("token.pkce.missing_code_verifier", exception.detailsId)
    }

    @Test
    fun `verifyCodeVerifier - Succeeds when no PKCE was used`() {
        assertDoesNotThrow {
            pkceManager.verifyCodeVerifier(null, null, null)
        }
    }

    @Test
    fun `verifyCodeVerifier - Fails when verifier is provided but no challenge was stored`() {
        val exception = assertThrows<BusinessException> {
            pkceManager.verifyCodeVerifier("some-verifier", null, null)
        }
        assertEquals("token.pkce.unexpected_code_verifier", exception.detailsId)
    }

    @Test
    fun `verifyCodeVerifier - Defaults to S256 when method is null`() {
        val codeVerifier = "test-verifier-string-for-pkce"
        val codeChallenge = computeS256Challenge(codeVerifier)

        assertDoesNotThrow {
            pkceManager.verifyCodeVerifier(codeVerifier, codeChallenge, null)
        }
    }
}
