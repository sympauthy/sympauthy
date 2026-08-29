package com.sympauthy.business.model.oauth2

/**
 * A DPoP proof (RFC 9449) that has already been validated.
 */
data class DpopProof(
    /**
     * The base64url-encoded JWK SHA-256 thumbprint (RFC 7638) of the public key the proof was
     * signed with. A token issued for this proof is bound to that key and may only be presented
     * with a further proof carrying the same one.
     */
    val jkt: String
)
