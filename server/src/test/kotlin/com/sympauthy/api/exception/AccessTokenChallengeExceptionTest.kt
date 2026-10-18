package com.sympauthy.api.exception

import com.sympauthy.business.model.oauth2.OAuth2ErrorCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INSUFFICIENT_SCOPE
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_TOKEN
import com.sympauthy.security.AccessTokenScheme
import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.FORBIDDEN
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccessTokenChallengeExceptionTest {

    @Test
    fun `headers - Challenge under both schemes with no error where no token was presented`() {
        val exception = challengeOf(UNAUTHORIZED, scheme = null, error = null)

        assertEquals(
            """Bearer realm="sympauthy", DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Carry the error on the Bearer challenge of a token presented as a bearer token`() {
        val exception = challengeOf(UNAUTHORIZED, BEARER, INVALID_TOKEN)

        assertEquals(
            """Bearer realm="sympauthy", error="invalid_token", DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Carry the error on the DPoP challenge of a token presented under the DPoP scheme`() {
        val exception = challengeOf(UNAUTHORIZED, DPOP, INVALID_DPOP_PROOF)

        assertEquals(
            """Bearer realm="sympauthy", DPoP realm="sympauthy", error="invalid_dpop_proof", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `toAccessTokenChallengeException - Keep the code, the description and the values`() {
        val exception = httpExceptionOf(FORBIDDEN, "forbidden", "description.forbidden", "scope" to "openid")
            .toAccessTokenChallengeException(FORBIDDEN, BEARER, INSUFFICIENT_SCOPE)

        assertEquals(FORBIDDEN, exception.status)
        assertEquals("forbidden", exception.detailsId)
        assertEquals("description.forbidden", exception.descriptionId)
        assertEquals(mapOf("scope" to "openid"), exception.values)
        assertEquals(BEARER, exception.scheme)
        assertEquals(INSUFFICIENT_SCOPE, exception.error)
    }

    private fun challengeOf(status: HttpStatus, scheme: AccessTokenScheme?, error: OAuth2ErrorCode?) =
        AccessTokenChallengeException(
            status = status,
            recoverable = false,
            detailsId = "unauthorized",
            descriptionId = "description.unauthorized",
            scheme = scheme,
            error = error
        )

    private companion object {
        const val ALGS = "RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512"
    }
}
