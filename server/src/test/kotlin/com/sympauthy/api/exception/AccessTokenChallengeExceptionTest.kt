package com.sympauthy.api.exception

import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientScope
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InsufficientUserAuthentication
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidDpopProof
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.InvalidToken
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge.UnboundToken
import com.sympauthy.security.AccessTokenScheme
import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import io.micronaut.http.HttpStatus.FORBIDDEN
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class AccessTokenChallengeExceptionTest {

    @Test
    fun `headers - Challenge under both schemes with no error where no token was presented`() {
        val exception = challengeOf(scheme = null, challenges = emptyList())

        assertEquals(
            """Bearer realm="sympauthy", DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Carry the error on the Bearer challenge of a token presented as a bearer token`() {
        val exception = challengeOf(BEARER, listOf(InvalidToken))

        assertEquals(
            """Bearer realm="sympauthy", error="invalid_token", DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Carry the error on the DPoP challenge of a token presented under the DPoP scheme`() {
        val exception = challengeOf(DPOP, listOf(InvalidDpopProof))

        assertEquals(
            """Bearer realm="sympauthy", DPoP realm="sympauthy", error="invalid_dpop_proof", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Name the scopes an insufficient_scope knows, space-separated`() {
        val exception = challengeOf(BEARER, listOf(InsufficientScope(listOf("profile:write", "email:write"))))

        assertEquals(
            """Bearer realm="sympauthy", error="insufficient_scope", scope="profile:write email:write", """ +
                """DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Name no scope where the insufficient_scope does not know it`() {
        val exception = challengeOf(BEARER, listOf(InsufficientScope(emptyList())))

        assertEquals(
            """Bearer realm="sympauthy", error="insufficient_scope", DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Repeat the scheme for each error, the scope before the age whatever order they came in`() {
        val exception = challengeOf(
            BEARER,
            listOf(InsufficientUserAuthentication(Duration.ofMinutes(5)), InsufficientScope(listOf("profile:write")))
        )

        assertEquals(
            """Bearer realm="sympauthy", error="insufficient_scope", scope="profile:write", """ +
                """Bearer realm="sympauthy", error="insufficient_user_authentication", """ +
                """error_description="A more recent authentication is required", max_age="300", """ +
                """DPoP realm="sympauthy", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Challenge an unbound token on the DPoP challenge, leaving the Bearer one without an error`() {
        val exception = challengeOf(BEARER, listOf(UnboundToken))

        assertEquals(
            """Bearer realm="sympauthy", DPoP realm="sympauthy", error="invalid_token", """ +
                """error_description="A token bound to a key is required", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `headers - Split the errors between the schemes that carry them`() {
        val exception = challengeOf(BEARER, listOf(UnboundToken, InsufficientScope(listOf("profile:write"))))

        assertEquals(
            """Bearer realm="sympauthy", error="insufficient_scope", scope="profile:write", """ +
                """DPoP realm="sympauthy", error="invalid_token", """ +
                """error_description="A token bound to a key is required", algs="$ALGS"""",
            exception.headers[WWW_AUTHENTICATE]
        )
    }

    @Test
    fun `status - Answer 403 where insufficient_scope is the only challenge`() {
        assertEquals(FORBIDDEN, challengeOf(BEARER, listOf(InsufficientScope(listOf("profile:write")))).status)
    }

    @Test
    fun `status - Answer 401 where another challenge comes beside insufficient_scope`() {
        val exception = challengeOf(BEARER, listOf(InsufficientScope(listOf("profile:write")), UnboundToken))

        assertEquals(UNAUTHORIZED, exception.status)
    }

    @Test
    fun `status - Answer 401 to a token that does not validate and to a request that presented none`() {
        assertEquals(UNAUTHORIZED, challengeOf(BEARER, listOf(InvalidToken)).status)
        assertEquals(UNAUTHORIZED, challengeOf(scheme = null, challenges = emptyList()).status)
    }

    @Test
    fun `Refuse an error without the scheme of the token it is carried for`() {
        assertThrows<IllegalArgumentException> { challengeOf(scheme = null, challenges = listOf(InvalidToken)) }
        assertThrows<IllegalArgumentException> { challengeOf(scheme = BEARER, challenges = emptyList()) }
    }

    @Test
    fun `withoutChallenge - Keep the status and the codes and answer with no header`() {
        val exception = challengeOf(BEARER, listOf(InvalidToken)).withoutChallenge()

        assertFalse(exception is AccessTokenChallengeException)
        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("unauthorized", exception.detailsId)
        assertEquals("description.unauthorized", exception.descriptionId)
        assertEquals(emptyMap<String, String>(), exception.headers)
    }

    @Test
    fun `toAccessTokenChallengeException - Keep the code, the description and the values`() {
        val exception = httpExceptionOf(FORBIDDEN, "forbidden", "description.forbidden", "scope" to "openid")
            .toAccessTokenChallengeException(BEARER, listOf(InsufficientScope(emptyList())))

        assertEquals(FORBIDDEN, exception.status)
        assertEquals("forbidden", exception.detailsId)
        assertEquals("description.forbidden", exception.descriptionId)
        assertEquals(mapOf("scope" to "openid"), exception.values)
        assertEquals(BEARER, exception.scheme)
        assertEquals(listOf(InsufficientScope(emptyList())), exception.challenges)
    }

    @Test
    fun `accessTokenChallengeExceptionOf - Name the scope in the body where it comes first`() {
        val challenges = listOf(InsufficientUserAuthentication(Duration.ofMinutes(5)), InsufficientScope(listOf("a")))

        val exception = accessTokenChallengeExceptionOf(BEARER, challenges)

        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("forbidden", exception.detailsId)
        assertEquals("description.forbidden", exception.descriptionId)
        assertTrue(exception.recoverable)
        assertEquals(challenges, exception.challenges)
    }

    @Test
    fun `accessTokenChallengeExceptionOf - Name the age in the body with the seconds it demands`() {
        val exception = accessTokenChallengeExceptionOf(
            DPOP,
            listOf(UnboundToken, InsufficientUserAuthentication(Duration.ofMinutes(5)))
        )

        assertEquals("authentication.insufficient_user_authentication", exception.detailsId)
        assertEquals("description.authentication.insufficient_user_authentication", exception.descriptionId)
        assertEquals(mapOf("maxAge" to "300"), exception.values)
    }

    @Test
    fun `accessTokenChallengeExceptionOf - Name the binding in the body where it is the only challenge`() {
        val exception = accessTokenChallengeExceptionOf(BEARER, listOf(UnboundToken))

        assertEquals("authentication.unbound_token", exception.detailsId)
        assertEquals("description.authentication.unbound_token", exception.descriptionId)
    }

    @Test
    fun `accessTokenChallengeExceptionOf - Refuse no challenge, and a token that does not validate`() {
        assertThrows<IllegalArgumentException> { accessTokenChallengeExceptionOf(BEARER, emptyList()) }
        assertThrows<IllegalArgumentException> { accessTokenChallengeExceptionOf(BEARER, listOf(InvalidToken)) }
        assertThrows<IllegalArgumentException> { accessTokenChallengeExceptionOf(DPOP, listOf(InvalidDpopProof)) }
    }

    private fun challengeOf(scheme: AccessTokenScheme?, challenges: List<WwwAuthenticateChallenge>) =
        AccessTokenChallengeException(
            recoverable = false,
            detailsId = "unauthorized",
            descriptionId = "description.unauthorized",
            scheme = scheme,
            challenges = challenges
        )

    private companion object {
        const val ALGS = "RS256 RS384 RS512 ES256 ES384 ES512 PS256 PS384 PS512"
    }
}
