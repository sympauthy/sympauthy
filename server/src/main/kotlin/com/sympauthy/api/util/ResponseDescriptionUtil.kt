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
        "- `token.revoked`: the access token was revoked.\n" +
        "- `token.client_disabled`: the client the access token was issued to is no longer in the " +
        "configuration.\n" +
        "- `token.actor_invalid`: the access token was obtained by token exchange, and the token " +
        "presented in that exchange no longer exists.\n" +
        "- `token.actor_revoked`: the access token was obtained by token exchange, and the token " +
        "presented in that exchange was revoked.\n" +
        "- `token.actor_expired`: the access token was obtained by token exchange, and the token " +
        "presented in that exchange has expired."

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
