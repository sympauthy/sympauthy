package com.sympauthy.api.util

/**
 * What the published specification says about the `401` every operation behind an access token
 * answers with: the refusals of `AccessTokenValidator`, and the framework's own where no token is sent.
 * A `403` names the scope its operation requires, so it is written per operation.
 */
const val UNAUTHORIZED_RESPONSE_DESCRIPTION =
    "- `unauthorized`: no access token is sent.\n" +
        "- `jwt.malformed`: the access token is not a signed JWT.\n" +
        "- `jwt.invalid_signature`: the access token is not signed with the key this server signs access " +
        "tokens with.\n" +
        "- `jwt.expired`: the access token has expired.\n" +
        "- `token.invalid_token_id`: this server issued no such access token.\n" +
        "- `grant.revoked`: the grant the access token was issued under was revoked.\n" +
        "- `grant.client_disabled`: the client the grant of the access token was opened for is no longer " +
        "in the configuration.\n" +
        "- `grant.actor_invalid`: the access token was obtained by token exchange, and the grant of the " +
        "token presented in that exchange no longer exists.\n" +
        "- `grant.actor_revoked`: the access token was obtained by token exchange, and the grant of the " +
        "token presented in that exchange was revoked.\n" +
        "- `grant.actor_expired`: the access token was obtained by token exchange, and every token of the " +
        "grant of the token presented in that exchange has expired.\n" +
        "- `dpop.bound_token_as_bearer`: the access token is bound to a DPoP key and is sent as " +
        "`Authorization: Bearer`.\n" +
        "- `dpop.unbound_token`: the access token is sent as `Authorization: DPoP` and is bound to no DPoP " +
        "key.\n" +
        "- `dpop.missing_header`: the access token is sent as `Authorization: DPoP` with no `DPoP` header.\n" +
        "- `dpop.multiple_headers`: the `DPoP` header is sent more than once.\n" +
        "- `dpop.malformed`: the `DPoP` header is not a signed JWT.\n" +
        "- `dpop.invalid_type`: the `typ` of the DPoP proof is not `dpop+jwt`.\n" +
        "- `dpop.unsupported_algorithm`: the DPoP proof is signed with an algorithm " +
        "`dpop_signing_alg_values_supported` does not list.\n" +
        "- `dpop.private_key_in_header`: the `jwk` of the DPoP proof holds a private key.\n" +
        "- `dpop.invalid_signature`: the DPoP proof does not verify against its `jwk`.\n" +
        "- `dpop.missing_claims`: the DPoP proof has no `jwk`, `jti`, `htm`, `htu` or `iat`.\n" +
        "- `dpop.invalid_htm`: the `htm` of the DPoP proof is not the method of the request.\n" +
        "- `dpop.invalid_htu`: the `htu` of the DPoP proof is not the URL of the request.\n" +
        "- `dpop.expired`: the `iat` of the DPoP proof is more than 60 seconds away from the time of the " +
        "request.\n" +
        "- `dpop.invalid_ath`: the `ath` of the DPoP proof is missing or is not the hash of the access token.\n" +
        "- `dpop.mismatching_key`: the DPoP proof is signed with another key than the one the access token " +
        "is bound to."

/**
 * The line of the `400` an operation answers with when a value sent for one of its parameters cannot be
 * read as the parameter's type.
 */
const val PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION =
    "- `parameter.malformed`: a parameter cannot be read as its type."

/**
 * The lines of the `400` an operation taking a JSON body answers with when the body cannot be read.
 */
const val BODY_BAD_REQUEST_DESCRIPTION =
    "- `body.malformed`: the body is not JSON, or a property in it is missing or holds a value of the " +
        "wrong type.\n" +
        "- `body.missing`: no body is sent."

/**
 * The lines of the `400` an operation creating an invitation answers with when it refuses the
 * `expires_at` of its body.
 */
const val INVITATION_EXPIRES_AT_BAD_REQUEST_DESCRIPTION =
    "- `invitation.expires_at.malformed`: `expires_at` is not a date and time with no zone, such as " +
        "`2026-12-01T00:00:00`.\n" +
        "- `invitation.expires_at.past`: `expires_at` is not in the future."

/**
 * What the published specification says about the `413` every operation taking a body answers with.
 */
const val BODY_TOO_LARGE_RESPONSE_DESCRIPTION = "- `body.too_large`: the body is larger than this server accepts."

/**
 * The lines of the `400` an operation answers with when it refuses the `return_uri` or the `cancel_uri`
 * of its body, each checked against the client the interactive flow session returns into.
 */
const val RETURN_URI_BAD_REQUEST_DESCRIPTION =
    "- `client.redirect_uri.missing`: `return_uri` is absent or blank, or `cancel_uri` is blank.\n" +
        "- `client.redirect_uri.invalid`: `return_uri` or `cancel_uri` cannot be read as a URL.\n" +
        "- `client.redirect_uri.not_allowed`: `return_uri` or `cancel_uri` is not one of the client's " +
        "`clients.<id>.allowed-redirect-uris`."

/**
 * The lines of the `400` an operation answers with when a claim value it was sent fails the validation
 * of the claim's type or of the values the claim allows.
 */
const val CLAIM_VALUE_BAD_REQUEST_DESCRIPTION =
    "- `user.claim_value_validator.invalid_boolean`: a value is not `true` or `false` for a claim of type " +
        "`boolean`.\n" +
        "- `user.claim_value_validator.invalid_date`: a value is not a date written `YYYY-MM-DD` for a claim " +
        "of type `date`.\n" +
        "- `user.claim_value_validator.invalid_email`: a value is not an email address for a claim of type " +
        "`email`.\n" +
        "- `user.claim_value_validator.invalid_number`: a value is not a whole number for a claim of type " +
        "`number`.\n" +
        "- `user.claim_value_validator.invalid_phone_number`: a value is not an E.164 phone number for a " +
        "claim of type `phone_number`.\n" +
        "- `user.claim_value_validator.invalid_time_zone`: a value is not a time zone for a claim of type " +
        "`timezone`.\n" +
        "- `user.claim_value_validator.invalid_value`: a value is not one its claim allows."
