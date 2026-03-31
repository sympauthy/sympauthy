package com.sympauthy.business.model.user.claim

/**
 * A claim defined in the OpenID Connect specification, collected by this authorization server as a first-party.
 *
 * Each standard claim is tied to an OpenID scope (e.g. `profile`, `email`, `address`).
 * Access is gated by that scope:
 * - **User read/write**: allowed when the claim's scope is among the consented scopes.
 *   The end-user can provide values during the authorization flow's claims collection step
 *   (for claims where [userInputted] is `true`).
 * - **Client read**: allowed when the claim's scope is among the consented scopes.
 *   Included in ID tokens and userinfo responses accordingly.
 * - **Client write**: never allowed. Standard claims are owned by the end-user and
 *   cannot be modified by clients.
 */
class StandardClaim(
    openIdClaim: OpenIdClaim,
    enabled: Boolean,
    required: Boolean,
    allowedValues: List<Any>?
) : Claim(
    id = openIdClaim.id,
    verifiedId = openIdClaim.verifiedId,
    dataType = openIdClaim.type,
    group = openIdClaim.group,
    enabled = enabled,
    required = required,
    userInputted = !openIdClaim.generated,
    allowedValues = allowedValues
) {
    private val scope = openIdClaim.scope.scope

    override fun belongsToScope(scope: String) = this.scope == scope

    override fun canBeReadByUser(scopes: List<String>) = scopes.any { this.scope == it }

    override fun canBeWrittenByUser(scopes: List<String>) = scopes.any { this.scope == it }

    override fun canBeReadByClient(scopes: List<String>) = scopes.any { this.scope == it }

    override fun canBeWrittenByClient(scopes: List<String>) = false
}
