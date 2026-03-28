package com.sympauthy.business.model.oauth2

/**
 * Represents a validated DPoP proof (RFC 9449).
 *
 * @param jkt The base64url-encoded JWK SHA-256 Thumbprint (RFC 7638) of the public key
 *            from the DPoP proof. Used to bind tokens to this key.
 */
data class DpopProof(
    val jkt: String
)
