package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.util.*

/**
 * Handles PKCE (Proof Key for Code Exchange, RFC 7636) verification during token exchange.
 */
@Singleton
class PkceManager {

    /**
     * Verifies the `code_verifier` against the stored `code_challenge` and `code_challenge_method`.
     *
     * - If no challenge was stored, `code_verifier` must be absent (no PKCE flow).
     * - If a challenge was stored, `code_verifier` is required and must match.
     */
    fun verifyCodeVerifier(
        codeVerifier: String?,
        codeChallenge: String?,
        codeChallengeMethod: CodeChallengeMethod?
    ) {
        if (codeChallenge == null) {
            // No PKCE was used during authorization; code_verifier should not be present
            if (!codeVerifier.isNullOrBlank()) {
                throw businessExceptionOf(
                    detailsId = "token.pkce.unexpected_code_verifier"
                )
            }
            return
        }

        // PKCE was used; code_verifier is required
        if (codeVerifier.isNullOrBlank()) {
            throw businessExceptionOf(
                detailsId = "token.pkce.missing_code_verifier"
            )
        }

        val method = codeChallengeMethod ?: CodeChallengeMethod.S256
        val computedChallenge = when (method) {
            CodeChallengeMethod.S256 -> computeS256Challenge(codeVerifier)
        }

        if (computedChallenge != codeChallenge) {
            throw businessExceptionOf(
                detailsId = "token.pkce.invalid_code_verifier"
            )
        }
    }

    internal fun computeS256Challenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
