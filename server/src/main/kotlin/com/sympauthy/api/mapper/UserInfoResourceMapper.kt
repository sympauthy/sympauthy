package com.sympauthy.api.mapper

import com.sympauthy.api.resource.openid.AddressResource
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import jakarta.inject.Singleton
import java.time.LocalDate
import java.util.*

@Singleton
class UserInfoResourceMapper(
    private val generatedClaimsManager: GeneratedClaimsManager
) {


    /**
     * The `/userinfo` answer for [userId], built from the [claims] a caller may read.
     *
     * A `<claim>_verified` companion is answered beside the claim it answers for and is false where that
     * value is unverified, which is what the id token claims for the same account — a client reading one
     * endpoint and then the other is owed the same shape from both.
     */
    suspend fun toResource(userId: UUID, claims: List<CollectedClaim>): UserInfoResource {
        val claimById = claims.associateBy { it.claim.id }
        val addressClaims = claims.filter { it.claim.group == ClaimGroup.ADDRESS }

        return UserInfoResource(
            sub = generatedClaimsManager.computeSubject(userId),
            name = claimById.stringOrNull(OpenIdConnectClaimId.NAME),
            givenName = claimById.stringOrNull(OpenIdConnectClaimId.GIVEN_NAME),
            familyName = claimById.stringOrNull(OpenIdConnectClaimId.FAMILY_NAME),
            middleName = claimById.stringOrNull(OpenIdConnectClaimId.MIDDLE_NAME),
            nickname = claimById.stringOrNull(OpenIdConnectClaimId.NICKNAME),
            preferredUsername = claimById.stringOrNull(OpenIdConnectClaimId.PREFERRED_USERNAME),
            profile = claimById.stringOrNull(OpenIdConnectClaimId.PROFILE),
            picture = claimById.stringOrNull(OpenIdConnectClaimId.PICTURE),
            website = claimById.stringOrNull(OpenIdConnectClaimId.WEBSITE),
            email = claimById.stringOrNull(OpenIdConnectClaimId.EMAIL),
            emailVerified = claimById[OpenIdConnectClaimId.EMAIL]?.let { it.verified ?: false },
            gender = claimById.stringOrNull(OpenIdConnectClaimId.GENDER),
            birthDate = claimById.stringOrNull(OpenIdConnectClaimId.BIRTH_DATE)?.let(LocalDate::parse),
            zoneInfo = claimById.stringOrNull(OpenIdConnectClaimId.ZONE_INFO),
            locale = claimById.stringOrNull(OpenIdConnectClaimId.LOCALE),
            phoneNumber = claimById.stringOrNull(OpenIdConnectClaimId.PHONE_NUMBER),
            phoneNumberVerified = claimById[OpenIdConnectClaimId.PHONE_NUMBER]?.let { it.verified ?: false },
            address = toAddressResource(addressClaims),
            updatedAt = generatedClaimsManager.computeUpdatedAt(userId)
        )
    }

    /**
     * The `address` object OpenID Connect Core §5.1.1 defines, assembled from the [addressClaims] of the
     * group, or null where none of them carries a value. Every member of it is a string there whatever
     * type the claim behind it was configured as, so a component is rendered rather than left out:
     * `docs/design-faq.md`.
     */
    private fun toAddressResource(addressClaims: List<CollectedClaim>): AddressResource? {
        if (addressClaims.isEmpty()) return null
        val addressById = addressClaims.associate { it.claim.id to it.value?.toString() }
        if (addressById.values.all { it == null }) return null

        val streetAddress = addressById[OpenIdConnectClaimId.STREET_ADDRESS]
        val locality = addressById[OpenIdConnectClaimId.LOCALITY]
        val region = addressById[OpenIdConnectClaimId.REGION]
        val postalCode = addressById[OpenIdConnectClaimId.POSTAL_CODE]
        val country = addressById[OpenIdConnectClaimId.COUNTRY]

        val formatted = listOfNotNull(
            streetAddress,
            listOfNotNull(locality, region, postalCode)
                .joinToString(", ")
                .ifBlank { null },
            country
        ).joinToString("\n").ifBlank { null }

        return AddressResource(
            formatted = formatted,
            streetAddress = streetAddress,
            locality = locality,
            region = region,
            postalCode = postalCode,
            country = country
        )
    }

    private fun Map<String, CollectedClaim>.stringOrNull(claimId: String): String? {
        return this[claimId]?.value as? String
    }
}
