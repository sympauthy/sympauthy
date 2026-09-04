package com.sympauthy.business.model.user

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Information about the user either:
 * - collected by us as a first party.
 * - collected from a third-party provider.
 *
 * The list of information we can collect is based on the list of information exposed by the
 * [OpenId user info endpoint](https://openid.net/specs/openid-connect-core-1_0.html#UserInfo).
 */
data class RawProviderClaims(
    val subject: String,

    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val nickname: String? = null,

    val preferredUsername: String? = null,
    val profile: String? = null,
    val picture: String? = null,
    val website: String? = null,

    val email: String? = null,
    val emailVerified: Boolean? = null,

    val gender: String? = null,
    val birthDate: LocalDate? = null,

    val zoneInfo: String? = null,
    val locale: String? = null,

    val phoneNumber: String? = null,
    val phoneNumberVerified: Boolean? = null,

    val streetAddress: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,

    val updatedAt: LocalDateTime? = null
) {
    /**
     * Return the value of the given [claim] as a string, or null if not available.
     * Returns null for custom claims that have no OpenID Connect equivalent.
     */
    fun getClaimValueOrNull(claim: Claim): String? = getClaimValueOrNull(claim.id)

    /**
     * Return the value of the claim identified by [claimId] as a string, or null if not available.
     */
    fun getClaimValueOrNull(claimId: String): String? = when (claimId) {
        OpenIdConnectClaimId.SUB -> subject
        OpenIdConnectClaimId.NAME -> name
        OpenIdConnectClaimId.GIVEN_NAME -> givenName
        OpenIdConnectClaimId.FAMILY_NAME -> familyName
        OpenIdConnectClaimId.MIDDLE_NAME -> middleName
        OpenIdConnectClaimId.NICKNAME -> nickname
        OpenIdConnectClaimId.PREFERRED_USERNAME -> preferredUsername
        OpenIdConnectClaimId.PROFILE -> profile
        OpenIdConnectClaimId.PICTURE -> picture
        OpenIdConnectClaimId.WEBSITE -> website
        OpenIdConnectClaimId.EMAIL -> email
        OpenIdConnectClaimId.GENDER -> gender
        OpenIdConnectClaimId.BIRTH_DATE -> birthDate?.toString()
        OpenIdConnectClaimId.ZONE_INFO -> zoneInfo
        OpenIdConnectClaimId.LOCALE -> locale
        OpenIdConnectClaimId.PHONE_NUMBER -> phoneNumber
        OpenIdConnectClaimId.STREET_ADDRESS -> streetAddress
        OpenIdConnectClaimId.LOCALITY -> locality
        OpenIdConnectClaimId.REGION -> region
        OpenIdConnectClaimId.POSTAL_CODE -> postalCode
        OpenIdConnectClaimId.COUNTRY -> country
        OpenIdConnectClaimId.UPDATED_AT -> updatedAt?.toString()
        else -> null
    }
}
