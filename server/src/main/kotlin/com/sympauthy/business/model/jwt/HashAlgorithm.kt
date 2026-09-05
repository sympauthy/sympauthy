package com.sympauthy.business.model.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.util.Base64URL
import java.nio.charset.StandardCharsets.US_ASCII
import java.security.MessageDigest

/**
 * Digest a JWT signing algorithm hashes with, and so the one an `at_hash` beside a token it signed is
 * computed with.
 *
 * The set is the sizes a JOSE algorithm name can end in, rather than the sizes this server signs with:
 * [ofOrNull] reads the name a third-party provider sent, while [JwtAlgorithm] names its own member.
 */
enum class HashAlgorithm(
    /**
     * Name [MessageDigest] resolves this digest by.
     */
    private val digestName: String
) {
    SHA256("SHA-256"),
    SHA384("SHA-384"),
    SHA512("SHA-512");

    /**
     * The `at_hash` of [accessToken] as OpenID Connect Core §3.1.3.6 defines it: the base64url encoding of the
     * left half of the token's hash under this digest, the token itself being read as ASCII.
     *
     * It is the whole of what the claim asserts on both sides of the protocol — this server writes what it
     * returns beside an id token it issues, and compares it to what a provider's id token carries.
     */
    fun atHash(accessToken: String): String {
        val hash = MessageDigest.getInstance(digestName).digest(accessToken.toByteArray(US_ASCII))
        return Base64URL.encode(hash.copyOf(hash.size / 2)).toString()
    }

    companion object {
        /**
         * The digest [algorithm] signs with, or null where its name ends in none of the sizes named here.
         *
         * Only an algorithm this server did not choose reaches that null, so a caller reading it is one
         * validating a third-party provider's token, and skipping is what it does about it.
         */
        fun ofOrNull(algorithm: JWSAlgorithm): HashAlgorithm? = when {
            algorithm.name.endsWith("256") -> SHA256
            algorithm.name.endsWith("384") -> SHA384
            algorithm.name.endsWith("512") -> SHA512
            else -> null
        }
    }
}
