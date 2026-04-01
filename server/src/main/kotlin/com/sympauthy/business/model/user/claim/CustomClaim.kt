package com.sympauthy.business.model.user.claim

/**
 * A custom claim defined in the YAML configuration that is not part of the OpenID Connect specification.
 *
 * Custom claims are designed to be managed by clients through the client API
 * (`PATCH /api/v1/client/users/{userId}/claims`), not collected from the end-user during
 * the authorization flow. This is enforced by [userInputted] being set to `false`, which
 * causes the [ClaimsController] to filter them out before any write operation.
 *
 * Since they are client-only, custom claims are not readable or writable by the end-user.
 * They are always readable and writable by clients regardless of the consented scopes.
 */
class CustomClaim(
    id: String,
    dataType: ClaimDataType,
    required: Boolean,
    allowedValues: List<Any>?
): Claim(
    id = id,
    enabled = true,
    verifiedId = null, // Add support for verification on custom claim.
    dataType = dataType,
    group = null,
    required = required,
    userInputted = false,
    allowedValues = allowedValues
) {
    override fun belongsToScope(scope: String) = false

    override fun canBeReadByUser(scopes: List<String>) = false

    override fun canBeWrittenByUser(scopes: List<String>) = false

    override fun canBeReadByClient(scopes: List<String>) = true

    override fun canBeWrittenByClient(scopes: List<String>) = true
}
