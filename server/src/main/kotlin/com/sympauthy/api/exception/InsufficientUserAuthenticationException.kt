package com.sympauthy.api.exception

import io.micronaut.http.HttpStatus.UNAUTHORIZED
import java.time.Duration

/**
 * An operation refused because the authentication behind the token presented is older than it allows,
 * answered with the challenge RFC 9470 §3 defines.
 *
 * It is a [LocalizedHttpException] like any other refusal of the `api` layer, so the body a client reads
 * is [the API standard's](https://github.com/sympauthy/sympauthy/blob/main/docs/standards/api-standard.md#errors)
 * under `authentication.insufficient_user_authentication`; what makes it its own type is the
 * [challenge] beside that body, which is how a client reading headers learns the same thing.
 *
 * The client's answer is to re-authorize with `max_age` and retry with the token it is issued. A public
 * client can do that — authorizing is a redirect, and nothing in the challenge needs a secret.
 *
 * Whether an authentication is recent enough is
 * [the claim's][com.sympauthy.business.model.user.claim.Claim.isAuthenticationRecentEnough]
 * question, and a caller refused there raises this with the age that claim declared.
 */
class InsufficientUserAuthenticationException(
    /**
     * How recent the authentication has to be for the refused operation. It is rendered into the
     * challenge in whole seconds, which is the unit `max_age` is written in everywhere it appears —
     * on the authorize endpoint and in the challenge alike — so an age configured with a finer
     * resolution than a second reaches the client rounded down.
     */
    val maxAge: Duration
) : LocalizedHttpException(
    status = UNAUTHORIZED,
    recoverable = true,
    detailsId = "authentication.insufficient_user_authentication",
    descriptionId = "description.authentication.insufficient_user_authentication",
    values = mapOf("maxAge" to maxAge.seconds.toString())
) {

    /**
     * The `WWW-Authenticate` value this refusal answers with.
     *
     * It names `max_age` alone of the two parameters RFC 9470 defines: this server publishes no
     * vocabulary of authentication strengths, so it has no `acr_values` to ask for.
     */
    override val challenge: String
        get() = """Bearer error="$ERROR", error_description="$ERROR_DESCRIPTION", max_age="${maxAge.seconds}""""

    companion object {
        /**
         * The `error` parameter RFC 9470 §3 names. The body beside the challenge carries the same word
         * under the `authentication` domain, and the two are written apart because they are two
         * contracts: this one is the specification's token, and the other is a message-bundle key.
         */
        const val ERROR = "insufficient_user_authentication"

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
