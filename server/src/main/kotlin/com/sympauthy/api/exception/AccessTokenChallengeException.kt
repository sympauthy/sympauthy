package com.sympauthy.api.exception

import com.sympauthy.business.manager.auth.oauth2.DpopManager.Companion.SUPPORTED_ALGORITHMS
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INSUFFICIENT_SCOPE
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INSUFFICIENT_USER_AUTHENTICATION
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TOKEN
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientScope
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientUserAuthentication
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidDpopProof
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidToken
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.UnboundToken
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.security.AccessTokenScheme
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.FORBIDDEN
import io.micronaut.http.HttpStatus.UNAUTHORIZED

/**
 * A request to a route an access token is accepted on, refused for the token it presented or for presenting
 * none, answered with a challenge per [AccessTokenScheme]: the one RFC 6750 §3 defines, and the one RFC 9449
 * §7.1 defines beside it.
 *
 * It is a [LocalizedHttpException] like any other refusal of the `api` layer, so the body a client reads is
 * [the API standard's](https://github.com/sympauthy/sympauthy/blob/main/docs/standards/api-standard.md#errors);
 * what makes it its own type is the challenges among its [headers], which is how a client reading headers
 * learns the same thing. Its status is decided by the [challenges] it carries rather than by its caller. Which
 * refusal carries which challenge, and how several are written into one header, is `docs/design/security.md`'s.
 */
class AccessTokenChallengeException(
    recoverable: Boolean,
    detailsId: String,
    descriptionId: String?,
    values: Map<String, String> = emptyMap(),
    /**
     * The scheme the refused token was presented under, whose challenge carries the [challenges] — all but an
     * [UnboundToken], which is met under [DPOP] alone. Null where the request presented no token.
     */
    val scheme: AccessTokenScheme?,
    /**
     * What a token presented next has to be for the request to be accepted, empty exactly where [scheme] is
     * null: a request that presented no token is told of no error, RFC 6750 §3.1. An [InvalidToken] or an
     * [InvalidDpopProof] comes alone, since a token that does not validate is refused for that and nothing
     * else, and an [UnboundToken] never comes under [DPOP], which only a bound token is accepted under.
     */
    val challenges: List<WwwAuthenticateChallenge>,
    throwable: Throwable? = null
) : LocalizedHttpException(
    status = statusOf(challenges),
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
) {

    init {
        require((scheme == null) == challenges.isEmpty()) {
            "A challenge names an error exactly where it names the scheme the refused token came under."
        }
        require(challenges.size == 1 || challenges.none { it is InvalidToken || it is InvalidDpopProof }) {
            "A token that does not validate is refused for that alone."
        }
        require(scheme != DPOP || UnboundToken !in challenges) {
            "A token presented under the DPoP scheme is bound to a key."
        }
    }

    /**
     * The `WWW-Authenticate` challenges this refusal answers with: for each [AccessTokenScheme], in the order
     * it declares them, a challenge per error that scheme carries, in the order [orderedChallenges] gives
     * them, or one carrying no error where it carries none. Every challenge names the [REALM], and the `DPoP`
     * one the [ALGS].
     *
     * Every value is written as a quoted-string, and none of them holds a quote or a backslash, which is what
     * keeps it valid without anything having to escape it.
     */
    override val headers: Map<String, String>
        get() {
            val ordered = orderedChallenges(scheme, challenges)
            val rendered = AccessTokenScheme.entries.flatMap { challengedScheme ->
                val carried = ordered.filter { (carrying, _) -> carrying == challengedScheme }
                if (carried.isEmpty()) {
                    listOf(render(challengedScheme, null))
                } else {
                    carried.map { (_, challenge) -> render(challengedScheme, challenge) }
                }
            }
            return mapOf(WWW_AUTHENTICATE to rendered.joinToString(", "))
        }

    private fun render(challengedScheme: AccessTokenScheme, challenge: WwwAuthenticateChallenge?): String {
        val parameters = buildList {
            add("realm" to REALM)
            if (challenge != null) {
                addAll(errorParametersOf(challenge))
            }
            if (challengedScheme == DPOP) {
                add("algs" to ALGS)
            }
        }
        return parameters.joinToString(", ", prefix = "${challengedScheme.scheme} ") { (name, value) ->
            "$name=\"$value\""
        }
    }

    /**
     * This refusal with the status, the codes and the values it holds, and no challenge.
     */
    fun withoutChallenge(): LocalizedHttpException = toHttpException(status)

    companion object {
        /**
         * The realm every challenge of an access token names.
         *
         * RFC 6750 §3 requires a `Bearer` challenge to carry a parameter, and one answering a request that
         * presented no token has no error to carry, so the realm is what it carries. RFC 9110 §11.5 makes the
         * realm, together with the root URL of the server, a protection space — the resources one set of
         * credentials is good for. An access token is accepted on the userinfo endpoint and on the client and
         * admin surfaces, so no one surface names the space it is good in, and the realm names the server. The
         * `Basic` challenge of a client names its surface instead, because client credentials are accepted on
         * that one alone.
         *
         * Nothing branches on the value, so renaming it breaks no client.
         */
        const val REALM = "sympauthy"

        /**
         * The `error_description` the challenge of an [InsufficientUserAuthentication] carries.
         *
         * Every `error_description` here is in English and unlocalized: a challenge is parsed by a client
         * rather than read by a person, and the sentence a person is shown is the body's, rendered in their own
         * locale.
         */
        const val INSUFFICIENT_USER_AUTHENTICATION_DESCRIPTION = "A more recent authentication is required"

        /**
         * The `error_description` the challenge of an [UnboundToken] carries, which is what tells it from a
         * token that does not validate: both are `invalid_token`.
         */
        const val UNBOUND_TOKEN_DESCRIPTION = "A token bound to a key is required"

        /**
         * The `algs` the `DPoP` challenge names: every algorithm a proof may be signed with.
         */
        private val ALGS = SUPPORTED_ALGORITHMS.joinToString(" ")

        /**
         * The `error` [challenge] is written with, and the parameters that follow it.
         *
         * An [InsufficientUserAuthentication] names `max_age` in whole seconds, the unit it is written in
         * everywhere it appears, so an age configured more finely than a second reaches the client rounded
         * down. It names `max_age` alone of the two parameters RFC 9470 defines: this server publishes no
         * vocabulary of authentication strengths, so it has no `acr_values` to ask for.
         */
        private fun errorParametersOf(challenge: WwwAuthenticateChallenge): List<Pair<String, String>> =
            when (challenge) {
                InvalidToken -> listOf("error" to INVALID_TOKEN.errorCode)
                InvalidDpopProof -> listOf("error" to INVALID_DPOP_PROOF.errorCode)
                is InsufficientScope -> buildList {
                    add("error" to INSUFFICIENT_SCOPE.errorCode)
                    if (challenge.scopes.isNotEmpty()) {
                        add("scope" to challenge.scopes.joinToString(" "))
                    }
                }

                is InsufficientUserAuthentication -> listOf(
                    "error" to INSUFFICIENT_USER_AUTHENTICATION.errorCode,
                    "error_description" to INSUFFICIENT_USER_AUTHENTICATION_DESCRIPTION,
                    "max_age" to challenge.maxAge.seconds.toString()
                )

                UnboundToken -> listOf(
                    "error" to INVALID_TOKEN.errorCode,
                    "error_description" to UNBOUND_TOKEN_DESCRIPTION
                )
            }
    }
}

/**
 * The status [challenge] is answered with on its own: `403` for an [InsufficientScope], RFC 6750 §3.1, and
 * `401` for every other, which a different token would be accepted in place of.
 */
private fun statusOf(challenge: WwwAuthenticateChallenge): HttpStatus = when (challenge) {
    is InsufficientScope -> FORBIDDEN
    InvalidToken, InvalidDpopProof, is InsufficientUserAuthentication, UnboundToken -> UNAUTHORIZED
}

/**
 * The status a refusal carrying [challenges] is answered with: `403` where every one of them is answered with
 * it, and `401` otherwise — including where there are none, since a request that presented no token is asked
 * for one.
 */
private fun statusOf(challenges: List<WwwAuthenticateChallenge>): HttpStatus {
    return if (challenges.isNotEmpty() && challenges.all { statusOf(it) == FORBIDDEN }) FORBIDDEN else UNAUTHORIZED
}

/**
 * The scheme whose challenge carries [challenge] for a token presented under [scheme]: that one, except for
 * an [UnboundToken], which is met under [DPOP] alone.
 */
private fun schemeCarrying(scheme: AccessTokenScheme?, challenge: WwwAuthenticateChallenge): AccessTokenScheme? =
    when (challenge) {
        UnboundToken -> DPOP
        else -> scheme
    }

/**
 * Each of [challenges] with the scheme carrying it, in the order the header writes them: by scheme, in the
 * order [AccessTokenScheme] declares them, then the scope, the age and the binding, so a client reading only
 * the first challenge of a scheme is told the condition the person has to be asked about.
 */
private fun orderedChallenges(
    scheme: AccessTokenScheme?,
    challenges: List<WwwAuthenticateChallenge>
): List<Pair<AccessTokenScheme, WwwAuthenticateChallenge>> {
    return challenges
        .mapNotNull { challenge -> schemeCarrying(scheme, challenge)?.let { it to challenge } }
        .sortedWith(compareBy({ (carrying, _) -> carrying.ordinal }, { (_, challenge) -> rankOf(challenge) }))
}

private fun rankOf(challenge: WwwAuthenticateChallenge): Int = when (challenge) {
    InvalidToken -> 0
    InvalidDpopProof -> 1
    is InsufficientScope -> 2
    is InsufficientUserAuthentication -> 3
    UnboundToken -> 4
}

/**
 * The refusal of an operation that the token presented under [scheme] was not allowed, and that a token
 * meeting every one of [challenges] would be, challenging it with all of them.
 *
 * Its body names the first of [challenges], in the order the header writes them, that is answered with the
 * status of the whole refusal: `forbidden` for an [InsufficientScope] standing alone,
 * `authentication.insufficient_user_authentication` for an [InsufficientUserAuthentication], and
 * `authentication.unbound_token` for an [UnboundToken]. It is recoverable, since the client retries with the
 * token it is issued after re-authorizing.
 *
 * [challenges] must not be empty, and must not hold an [InvalidToken] or an [InvalidDpopProof]: a token that
 * does not validate is refused by the code its validation failed with, which only the validator holds.
 */
fun accessTokenChallengeExceptionOf(
    scheme: AccessTokenScheme,
    challenges: List<WwwAuthenticateChallenge>
): AccessTokenChallengeException {
    fun refusal(detailsId: String, descriptionId: String, values: Map<String, String> = emptyMap()) =
        AccessTokenChallengeException(
            recoverable = true,
            detailsId = detailsId,
            descriptionId = descriptionId,
            values = values,
            scheme = scheme,
            challenges = challenges
        )

    val status = statusOf(challenges)
    val named = orderedChallenges(scheme, challenges)
        .map { (_, challenge) -> challenge }
        .firstOrNull { statusOf(it) == status }
    return when (named) {
        null -> throw IllegalArgumentException("A refusal to challenge names at least one challenge.")
        is InsufficientScope -> refusal(detailsId = "forbidden", descriptionId = "description.forbidden")
        is InsufficientUserAuthentication -> refusal(
            detailsId = "authentication.insufficient_user_authentication",
            descriptionId = "description.authentication.insufficient_user_authentication",
            values = mapOf("maxAge" to named.maxAge.seconds.toString())
        )

        UnboundToken -> refusal(
            detailsId = "authentication.unbound_token",
            descriptionId = "description.authentication.unbound_token"
        )

        InvalidToken, InvalidDpopProof -> throw IllegalArgumentException(
            "A token that does not validate is refused by the code its validation failed with."
        )
    }
}

/**
 * This failure as an [AccessTokenChallengeException] challenging the token presented under [scheme] with
 * [challenges], carrying the code, the description, the values and the recoverability it already holds.
 *
 * The status it recommends gives way to the one [challenges] decide.
 */
fun BusinessException.toAccessTokenChallengeException(
    scheme: AccessTokenScheme?,
    challenges: List<WwwAuthenticateChallenge>
) = AccessTokenChallengeException(
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    scheme = scheme,
    challenges = challenges,
    throwable = throwable
)
