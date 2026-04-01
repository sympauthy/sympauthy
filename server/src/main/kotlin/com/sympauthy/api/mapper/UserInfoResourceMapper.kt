package com.sympauthy.api.mapper

import com.sympauthy.api.resource.openid.AddressResource
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.OpenIdClaim
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
            name = claimById.stringOrNull(OpenIdClaim.Id.NAME),
            givenName = claimById.stringOrNull(OpenIdClaim.Id.GIVEN_NAME),
            familyName = claimById.stringOrNull(OpenIdClaim.Id.FAMILY_NAME),
            middleName = claimById.stringOrNull(OpenIdClaim.Id.MIDDLE_NAME),
            nickname = claimById.stringOrNull(OpenIdClaim.Id.NICKNAME),
            preferredUsername = claimById.stringOrNull(OpenIdClaim.Id.PREFERRED_USERNAME),
            profile = claimById.stringOrNull(OpenIdClaim.Id.PROFILE),
            picture = claimById.stringOrNull(OpenIdClaim.Id.PICTURE),
            website = claimById.stringOrNull(OpenIdClaim.Id.WEBSITE),
            email = claimById.stringOrNull(OpenIdClaim.Id.EMAIL),
            emailVerified = claimById[OpenIdClaim.Id.EMAIL]?.verified?.toString(),
            gender = claimById.stringOrNull(OpenIdClaim.Id.GENDER),
            birthDate = claimById.stringOrNull(OpenIdClaim.Id.BIRTH_DATE)?.let(LocalDate::parse),
            zoneInfo = claimById.stringOrNull(OpenIdClaim.Id.ZONE_INFO),
            locale = claimById.stringOrNull(OpenIdClaim.Id.LOCALE),
            phoneNumber = claimById.stringOrNull(OpenIdClaim.Id.PHONE_NUMBER),
            phoneNumberVerified = claimById[OpenIdClaim.Id.PHONE_NUMBER]?.verified,
            address = toAddressResource(addressClaims),
            updatedAt = claims.maxOfOrNull { it.collectionDate }
                ?.toInstant(ZoneOffset.UTC)?.epochSecond
        )
    }

    private fun toAddressResource(addressClaims: List<CollectedClaim>): AddressResource? {
        if (addressClaims.isEmpty()) return null
        val addressById = addressClaims.associate { it.claim.id to (it.value as? String) }
        if (addressById.values.all { it == null }) return null

        val streetAddress = addressById[OpenIdClaim.Id.STREET_ADDRESS]
        val locality = addressById[OpenIdClaim.Id.LOCALITY]
        val region = addressById[OpenIdClaim.Id.REGION]
        val postalCode = addressById[OpenIdClaim.Id.POSTAL_CODE]
        val country = addressById[OpenIdClaim.Id.COUNTRY]

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
