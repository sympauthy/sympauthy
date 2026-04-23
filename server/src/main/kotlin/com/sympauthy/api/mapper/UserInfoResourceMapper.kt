package com.sympauthy.api.mapper

import com.sympauthy.api.resource.openid.AddressResource
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@Singleton
class UserInfoResourceMapper {

    fun toResource(userId: UUID, claims: List<CollectedClaim>): UserInfoResource {
        val claimById = claims.associateBy { it.claim.id }
        val addressClaims = claims.filter { it.claim.group == ClaimGroup.ADDRESS }

        return UserInfoResource(
            sub = userId.toString(),
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
            emailVerified = claimById[OpenIdConnectClaimId.EMAIL]?.verified?.toString(),
            gender = claimById.stringOrNull(OpenIdConnectClaimId.GENDER),
            birthDate = claimById.stringOrNull(OpenIdConnectClaimId.BIRTH_DATE)?.let(LocalDate::parse),
            zoneInfo = claimById.stringOrNull(OpenIdConnectClaimId.ZONE_INFO),
            locale = claimById.stringOrNull(OpenIdConnectClaimId.LOCALE),
            phoneNumber = claimById.stringOrNull(OpenIdConnectClaimId.PHONE_NUMBER),
            phoneNumberVerified = claimById[OpenIdConnectClaimId.PHONE_NUMBER]?.verified,
            address = toAddressResource(addressClaims),
            updatedAt = claims.maxOfOrNull { it.collectionDate }
                ?.toInstant(ZoneOffset.UTC)?.epochSecond
        )
    }

    private fun toAddressResource(addressClaims: List<CollectedClaim>): AddressResource? {
        if (addressClaims.isEmpty()) return null
        val addressById = addressClaims.associate { it.claim.id to (it.value as? String) }
        if (addressById.values.all { it == null }) return null

        val streetAddress = addressById[OpenIdConnectClaimId.STREET_ADDRESS]
        val locality = addressById[OpenIdConnectClaimId.LOCALITY]
        val region = addressById[OpenIdConnectClaimId.REGION]
        val postalCode = addressById[OpenIdConnectClaimId.POSTAL_CODE]
        val country = addressById[OpenIdConnectClaimId.COUNTRY]

        val formatted = listOfNotNull(
            streetAddress,
            listOfNotNull(locality, region, postalCode)
                .joinToString(", ").ifBlank { null },
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
