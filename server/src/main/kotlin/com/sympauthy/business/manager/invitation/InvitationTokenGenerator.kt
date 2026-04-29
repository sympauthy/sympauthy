package com.sympauthy.business.manager.invitation

import jakarta.inject.Singleton
import java.security.SecureRandom
import java.util.*

/**
 * Generates and encodes invitation tokens.
 *
 * Tokens are random bytes, base64url-encoded (no padding) for safe use in URLs and API responses.
 */
@Singleton
class InvitationTokenGenerator {

    private val secureRandom = SecureRandom()
    private val base64UrlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val base64UrlDecoder = Base64.getUrlDecoder()

    /**
     * Generate [lengthInBytes] cryptographically secure random bytes.
     */
    fun generate(lengthInBytes: Int): ByteArray {
        val bytes = ByteArray(lengthInBytes)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /**
     * Encode [token] bytes to a base64url string (no padding).
     */
    fun encode(token: ByteArray): String {
        return base64UrlEncoder.encodeToString(token)
    }

    /**
     * Decode a base64url-encoded [encodedToken] back to bytes.
     */
    fun decode(encodedToken: String): ByteArray {
        return base64UrlDecoder.decode(encodedToken)
    }

    /**
     * Extract the first [length] characters of [encodedToken] as a prefix for identification.
     */
    fun extractPrefix(encodedToken: String, length: Int = PREFIX_LENGTH): String {
        return encodedToken.take(length)
    }

    companion object {
        const val PREFIX_LENGTH = 8
    }
}
