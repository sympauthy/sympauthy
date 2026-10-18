package com.sympauthy.api.exception

import com.sympauthy.business.manager.auth.oauth2.DpopManager.Companion.SUPPORTED_ALGORITHMS
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.exception.LocalizedException
import com.sympauthy.security.AccessTokenScheme
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import io.micronaut.http.HttpStatus

/**
 * A request to a route an access token is accepted on, refused for the token it presented or for presenting
 * none, answered with a challenge per [AccessTokenScheme]: the one RFC 6750 §3 defines, and the one RFC 9449
 * §7.1 defines beside it.
 *
 * It is a [LocalizedHttpException] like any other refusal of the `api` layer, so the body a client reads is
 * [the API standard's](https://github.com/sympauthy/sympauthy/blob/main/docs/standards/api-standard.md#errors);
 * what makes it its own type is the challenge among its [headers], which is how a client reading headers
 * learns the same thing. Which refusal carries which [error] is `docs/design/security.md`'s.
 */
open class AccessTokenChallengeException(
    status: HttpStatus,
    recoverable: Boolean,
    detailsId: String,
    descriptionId: String?,
    values: Map<String, String> = emptyMap(),
    /**
     * The scheme the refused token was presented under, whose challenge carries the [error]. Null where the
     * request presented no token.
     */
    val scheme: AccessTokenScheme?,
    /**
     * The `error` the challenge of [scheme] carries, null exactly where [scheme] is: a request that presented no
     * token is told of no error, RFC 6750 §3.1.
     */
    val error: OAuth2ErrorCode?,
    throwable: Throwable? = null
) : LocalizedHttpException(
    status = status,
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    throwable = throwable
) {

    /**
     * The parameters the challenge carrying the [error] names after it, in order: none for most refusals.
     */
    protected open val errorParameters: List<Pair<String, String>> get() = emptyList()

    /**
     * The `WWW-Authenticate` challenges this refusal answers with, one per [AccessTokenScheme] in the order it
     * declares them, each naming the [REALM].
     *
     * Every value is written as a quoted-string, and none of them holds a quote or a backslash, which is what
     * keeps it valid without anything having to escape it.
     */
    override val headers: Map<String, String>
        get() = mapOf(WWW_AUTHENTICATE to AccessTokenScheme.entries.joinToString(", ", transform = ::challengeOf))

    private fun challengeOf(challengedScheme: AccessTokenScheme): String {
        val parameters = buildList {
            add("realm" to REALM)
            if (challengedScheme == scheme && error != null) {
                add("error" to error.errorCode)
                addAll(errorParameters)
            }
            if (challengedScheme == DPOP) {
                add("algs" to SUPPORTED_ALGORITHMS.joinToString(" "))
            }
        }
        return parameters.joinToString(", ", prefix = "${challengedScheme.scheme} ") { (name, value) ->
            "$name=\"$value\""
        }
    }

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
    }
}

/**
 * This failure as an [AccessTokenChallengeException] answering with [httpStatus] and challenging the token
 * presented under [scheme] with [error], carrying the code, the description, the values and the recoverability
 * it already holds.
 */
fun LocalizedException.toAccessTokenChallengeException(
    httpStatus: HttpStatus,
    scheme: AccessTokenScheme?,
    error: OAuth2ErrorCode?
) = AccessTokenChallengeException(
    status = httpStatus,
    recoverable = recoverable,
    detailsId = detailsId,
    descriptionId = descriptionId,
    values = values,
    scheme = scheme,
    error = error,
    throwable = throwable
)
