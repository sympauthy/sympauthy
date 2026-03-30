package com.sympauthy.business.manager.user

import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.RawUserInfoBuilder
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.EMAIL
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.FAMILY_NAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.GENDER
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.GIVEN_NAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.LOCALE
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.MIDDLE_NAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.NAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.NICKNAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.PHONE_NUMBER
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.PICTURE
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.PREFERRED_USERNAME
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.PROFILE
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.WEBSITE
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.COUNTRY
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.LOCALITY
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.POSTAL_CODE
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.REGION
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.STREET_ADDRESS
import com.sympauthy.business.model.user.claim.OpenIdClaim.Id.ZONE_INFO
import com.sympauthy.util.nullIfBlank
import java.time.LocalDateTime
import java.util.*

/**
 * Merge the user info we collected with the ones provided by the third-party providers.
 *
 * - We apply the info collected by third-party providers (in [providerUserInfoList]) in chronological order.
 *   The chronological order is determined by the [ProviderUserInfo.updatedAt] field.
 *   The [ProviderUserInfo.changeDate] is used if the [ProviderUserInfo.updatedAt] field is null.
 *
 * - Then we apply the info we collected as a first-party that are stored in [collectedClaimList].
 *
 * - FIXME: Finally, we filter the user info that will be returned according to the [scopes].
 */
internal class ClaimsMerger(
    // private val user: User,
    private val userId: UUID,
    private val collectedClaimList: List<CollectedClaim>? = null,
    private val providerUserInfoList: List<ProviderUserInfo> = emptyList()
) {

    fun merge(): RawProviderClaims {
        return RawUserInfoBuilder(userId).apply {
            merge(this)
        }.build()
    }

    internal fun merge(builder: RawUserInfoBuilder) {
        providerUserInfoList.sortedBy(::getUpdatedAt)
            .fold(builder, ::apply)
        collectedClaimList?.let { apply(builder, it) }
    }

    internal fun getUpdatedAt(providerUserInfo: ProviderUserInfo): LocalDateTime {
        return providerUserInfo.userInfo.updatedAt ?: providerUserInfo.changeDate
    }

    internal fun apply(builder: RawUserInfoBuilder, providerUserInfo: ProviderUserInfo): RawUserInfoBuilder {
        val info = providerUserInfo.userInfo

        info.name.nullIfBlank()?.let(builder::withName)
        info.givenName.nullIfBlank()?.let(builder::withGivenName)
        info.familyName.nullIfBlank()?.let(builder::withFamilyName)
        info.middleName.nullIfBlank()?.let(builder::withMiddleName)
        info.nickname.nullIfBlank()?.let(builder::withNickname)

        info.preferredUsername.nullIfBlank()?.let(builder::withPreferredUsername)
        info.profile.nullIfBlank()?.let(builder::withProfile)
        info.picture.nullIfBlank()?.let(builder::withPicture)
        info.website.nullIfBlank()?.let(builder::withWebsite)

        if (info.email?.isNotBlank() == true) {
            builder.withEmail(info.email, info.emailVerified)
        }

        info.gender.nullIfBlank()?.let(builder::withGender)
        info.birthDate?.let(builder::withBirthDate)

        info.zoneInfo.nullIfBlank()?.let(builder::withZoneInfo)
        info.locale.nullIfBlank()?.let(builder::withLocale)

        if (info.phoneNumber?.isNotBlank() == true) {
            builder.withPhoneNumber(info.phoneNumber, info.phoneNumberVerified)
        }

        info.streetAddress.nullIfBlank()?.let(builder::withStreetAddress)
        info.locality.nullIfBlank()?.let(builder::withLocality)
        info.region.nullIfBlank()?.let(builder::withRegion)
        info.postalCode.nullIfBlank()?.let(builder::withPostalCode)
        info.country.nullIfBlank()?.let(builder::withCountry)

        getUpdatedAt(providerUserInfo).let(builder::withUpdateAt)
        return builder
    }

    internal fun apply(
        builder: RawUserInfoBuilder,
        collectedClaimList: List<CollectedClaim>
    ): RawUserInfoBuilder {
        var updatedAt: LocalDateTime? = null
        collectedClaimList.forEach { info ->
            when {
                info.claim.id == NAME && info.value is String -> builder.withName(info.value)
                info.claim.id == GIVEN_NAME && info.value is String -> builder.withGivenName(info.value)
                info.claim.id == FAMILY_NAME && info.value is String -> builder.withFamilyName(info.value)
                info.claim.id == MIDDLE_NAME && info.value is String -> builder.withMiddleName(info.value)
                info.claim.id == NICKNAME && info.value is String -> builder.withNickname(info.value)
                info.claim.id == PREFERRED_USERNAME && info.value is String -> builder.withPreferredUsername(info.value)
                info.claim.id == PROFILE && info.value is String -> builder.withProfile(info.value)
                info.claim.id == PICTURE && info.value is String -> builder.withPicture(info.value)
                info.claim.id == WEBSITE && info.value is String -> builder.withWebsite(info.value)
                info.claim.id == EMAIL && info.value is String -> builder.withEmail(info.value, info.verified ?: false)
                info.claim.id == GENDER && info.value is String -> builder.withGender(info.value)
                info.claim.id == ZONE_INFO && info.value is String -> builder.withZoneInfo(info.value)
                info.claim.id == LOCALE && info.value is String -> builder.withLocale(info.value)
                info.claim.id == PHONE_NUMBER && info.value is String -> builder.withPhoneNumber(info.value, info.verified ?: false)
                info.claim.id == STREET_ADDRESS && info.value is String -> builder.withStreetAddress(info.value)
                info.claim.id == LOCALITY && info.value is String -> builder.withLocality(info.value)
                info.claim.id == REGION && info.value is String -> builder.withRegion(info.value)
                info.claim.id == POSTAL_CODE && info.value is String -> builder.withPostalCode(info.value)
                info.claim.id == COUNTRY && info.value is String -> builder.withCountry(info.value)
            }
            if (updatedAt == null || updatedAt.isBefore(info.collectionDate)) {
                updatedAt = info.collectionDate
            }
        }

        /* FIXME
        if (collectedClaims.contains(BIRTH_DATE)) {
            builder.withBirthDate(info.birthDate)
        }
         */

        return builder
    }
}
