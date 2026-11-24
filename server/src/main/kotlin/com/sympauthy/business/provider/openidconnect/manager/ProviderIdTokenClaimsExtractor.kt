package com.sympauthy.business.provider.openidconnect.manager

import com.nimbusds.jwt.SignedJWT
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.ADDRESS
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.BIRTHDATE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.COUNTRY
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.EMAIL
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.EMAIL_VERIFIED
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.FAMILY_NAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.GENDER
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.GIVEN_NAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.LOCALE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.LOCALITY
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.MIDDLE_NAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.NAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.NICKNAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.PHONE_NUMBER
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.PHONE_NUMBER_VERIFIED
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.PICTURE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.POSTAL_CODE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.PREFERRED_USERNAME
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.PROFILE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.REGION
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.STREET_ADDRESS
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.SUB
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.UPDATED_AT
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.WEBSITE
import com.sympauthy.business.claim.model.OpenIdConnectClaimId.ZONEINFO
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.provider.model.RawProviderClaims
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Singleton
class ProviderIdTokenClaimsExtractor {

    /**
     * Decode the payload of an ID token JWT without verifying its signature.
     * Used when an OAuth2 provider unexpectedly returns an ID token but we have no JWKS to validate against.
     */
    fun decodeClaimsWithoutValidation(idTokenRaw: String): Map<String, Any> {
        val jwt = SignedJWT.parse(idTokenRaw)
        return jwt.jwtClaimsSet.claims
            .filterValues { it != null }
            .mapValues { it.value!! }
    }

    @Suppress("UNCHECKED_CAST")
    fun extractClaims(providerId: String, idTokenClaims: Map<String, Any>): RawProviderClaims {
        val subject = idTokenClaims[SUB]?.toString()
            ?: throw businessExceptionOf(
                "provider.user_info.missing_subject",
                "providerId" to providerId
            )

        val addressClaims = idTokenClaims[ADDRESS] as? Map<String, Any>

        return RawProviderClaims(
            subject = subject,
            name = idTokenClaims[NAME]?.toString(),
            givenName = idTokenClaims[GIVEN_NAME]?.toString(),
            familyName = idTokenClaims[FAMILY_NAME]?.toString(),
            middleName = idTokenClaims[MIDDLE_NAME]?.toString(),
            nickname = idTokenClaims[NICKNAME]?.toString(),
            preferredUsername = idTokenClaims[PREFERRED_USERNAME]?.toString(),
            profile = idTokenClaims[PROFILE]?.toString(),
            picture = idTokenClaims[PICTURE]?.toString(),
            website = idTokenClaims[WEBSITE]?.toString(),
            email = idTokenClaims[EMAIL]?.toString(),
            emailVerified = (idTokenClaims[EMAIL_VERIFIED] as? Boolean),
            gender = idTokenClaims[GENDER]?.toString(),
            birthdate = idTokenClaims[BIRTHDATE]?.toString()?.let { parseDateOrNull(it) },
            zoneinfo = idTokenClaims[ZONEINFO]?.toString(),
            locale = idTokenClaims[LOCALE]?.toString(),
            phoneNumber = idTokenClaims[PHONE_NUMBER]?.toString(),
            phoneNumberVerified = (idTokenClaims[PHONE_NUMBER_VERIFIED] as? Boolean),
            streetAddress = addressClaims?.get(STREET_ADDRESS)?.toString(),
            locality = addressClaims?.get(LOCALITY)?.toString(),
            region = addressClaims?.get(REGION)?.toString(),
            postalCode = addressClaims?.get(POSTAL_CODE)?.toString(),
            country = addressClaims?.get(COUNTRY)?.toString(),
            updatedAt = (idTokenClaims[UPDATED_AT] as? Number)?.toLong()?.let { epochSeconds ->
                LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"))
            }
        )
    }

    private fun parseDateOrNull(value: String): LocalDate? {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_DATE)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
