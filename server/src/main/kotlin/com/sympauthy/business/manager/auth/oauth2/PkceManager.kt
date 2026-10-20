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
     * Verify that [codeVerifier] is the one [codeChallenge] was derived from with [codeChallengeMethod], as
     * RFC 7636 §4.6 has it. Every authorization code carries a challenge, so a request without a verifier is
     * refused.
     *
     * Throws `token.pkce.missing_code_verifier` where [codeVerifier] is null or blank, and
     * `token.pkce.invalid_code_verifier` where it does not derive [codeChallenge].
     */
    fun verifyCodeVerifier(
        codeVerifier: String?,
        codeChallenge: String,
        codeChallengeMethod: CodeChallengeMethod
    ) {
        if (codeVerifier.isNullOrBlank()) {
            throw businessExceptionOf(
                detailsId = "token.pkce.missing_code_verifier"
            )
        }

        val computedChallenge = when (codeChallengeMethod) {
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
