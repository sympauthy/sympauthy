package com.sympauthy.api.exception

import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INSUFFICIENT_USER_AUTHENTICATION
import com.sympauthy.security.AccessTokenScheme
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import java.time.Duration

/**
 * An operation refused because the authentication behind the token presented is older than it allows,
 * answered with the challenge RFC 9470 §3 defines.
 *
 * It is an [AccessTokenChallengeException] like every refusal of a token, so the body a client reads is
 * [the API standard's](https://github.com/sympauthy/sympauthy/blob/main/docs/standards/api-standard.md#errors)
 * under `authentication.insufficient_user_authentication`, and the challenge of the scheme the token came under
 * carries [INSUFFICIENT_USER_AUTHENTICATION]; what makes it its own type is the age that challenge demands.
 *
 * The client's answer is to re-authorize with `max_age` and retry with the token it is issued. A public
 * client can do that — authorizing is a redirect, and nothing in the challenge needs a secret.
 *
 * Whether an authentication is recent enough for a write is the claim's ACL question, and a caller refused
 * there raises this with the age
 * [the challenge][com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientUserAuthentication]
 * of that refusal names.
 */
class InsufficientUserAuthenticationException(
    scheme: AccessTokenScheme,
    /**
     * How recent the authentication has to be for the refused operation. It is rendered into the
     * challenge in whole seconds, which is the unit `max_age` is written in everywhere it appears —
     * on the authorize endpoint and in the challenge alike — so an age configured with a finer
     * resolution than a second reaches the client rounded down.
     */
    val maxAge: Duration
) : AccessTokenChallengeException(
    status = UNAUTHORIZED,
    recoverable = true,
    detailsId = "authentication.insufficient_user_authentication",
    descriptionId = "description.authentication.insufficient_user_authentication",
    values = mapOf("maxAge" to maxAge.seconds.toString()),
    scheme = scheme,
    error = INSUFFICIENT_USER_AUTHENTICATION
) {

    /**
     * The `error_description` and the `max_age` the challenge names after its error.
     *
     * It names `max_age` alone of the two parameters RFC 9470 defines: this server publishes no
     * vocabulary of authentication strengths, so it has no `acr_values` to ask for.
     */
    override val errorParameters: List<Pair<String, String>>
        get() = listOf(
            "error_description" to ERROR_DESCRIPTION,
            "max_age" to maxAge.seconds.toString()
        )

    companion object {
        /**
         * The `error_description` the challenge carries, in English and unlocalized: a challenge is
         * parsed by a client rather than read by a person, and the sentence a person is shown is the
         * body's, rendered in their own locale.
         *
         * It holds no quote and no backslash, which is what keeps it a valid quoted-string without
         * anything having to escape it.
         */
        const val ERROR_DESCRIPTION = "A more recent authentication is required"
    }
}
