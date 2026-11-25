package com.sympauthy.business.provider.openidconnect.manager

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.sympauthy.business.exception.BusinessException
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.text.ParseException
import java.time.LocalDateTime

class ProviderIdTokenClaimsExtractorTest {

    private val extractor = ProviderIdTokenClaimsExtractor()

    private val claimsSet = JWTClaimsSet.Builder().subject("subject").build()

    @Test
    fun `decodeClaimsWithoutValidation - Return the claims of a signed token`() {
        val idToken = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claimsSet)
            .apply { sign(MACSigner(ByteArray(32))) }
            .serialize()

        val claims = extractor.decodeClaimsWithoutValidation("test-provider", idToken)

        assertEquals(mapOf("sub" to "subject"), claims)
    }

    @Test
    fun `decodeClaimsWithoutValidation - Fail the session where the token is opaque`() {
        val failure = assertThrows<BusinessException> {
            extractor.decodeClaimsWithoutValidation("test-provider", "opaque-token")
        }

        assertInvalidIdToken(failure)
    }

    @Test
    fun `decodeClaimsWithoutValidation - Fail the session where the token is unsecured`() {
        val idToken = PlainJWT(claimsSet).serialize()

        val failure = assertThrows<BusinessException> {
            extractor.decodeClaimsWithoutValidation("test-provider", idToken)
        }

        assertInvalidIdToken(failure)
    }

    @Test
    fun `decodeClaimsWithoutValidation - Fail the session where the token is encrypted`() {
        val failure = assertThrows<BusinessException> {
            extractor.decodeClaimsWithoutValidation("test-provider", "header.key.iv.ciphertext.tag")
        }

        assertInvalidIdToken(failure)
    }

    private fun assertInvalidIdToken(failure: BusinessException) {
        assertEquals("provider.openid_connect.invalid_id_token", failure.detailsId)
        assertFalse(failure.recoverable)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.recommendedStatus)
        assertEquals("test-provider", failure.values["providerId"])
        assertTrue(failure.cause is ParseException)
    }

    @Test
    fun `extractClaims - Read updated_at as seconds since the epoch`() {
        val claims = extractor.extractClaims("test-provider", mapOf("sub" to "subject", "updated_at" to 86_400L))

        assertEquals(LocalDateTime.of(1970, 1, 2, 0, 0), claims.updatedAt)
    }

    @Test
    fun `extractClaims - Drop an updated_at beyond the range of an instant`() {
        val claims = extractor.extractClaims("test-provider", mapOf("sub" to "subject", "updated_at" to 1e20))

        assertNull(claims.updatedAt)
    }

    @Test
    fun `extractClaims - Drop an updated_at that is an instant but beyond the range of a date`() {
        val claims = extractor.extractClaims(
            "test-provider",
            mapOf("sub" to "subject", "updated_at" to 31_556_889_864_403_199L)
        )

        assertNull(claims.updatedAt)
    }
}
