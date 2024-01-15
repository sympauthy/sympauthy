package tournament.events.auth.business.manager.user

import tournament.events.auth.business.model.provider.ProviderUserInfo
import tournament.events.auth.business.model.user.CollectedUserInfo
import tournament.events.auth.business.model.user.RawUserInfo
import tournament.events.auth.business.model.user.RawUserInfoBuilder
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.FAMILY_NAME
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.GIVEN_NAME
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.MIDDLE_NAME
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.NAME
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.NICKNAME
import tournament.events.auth.business.model.user.claim.OpenIdClaim.Id.PREFERRED_USERNAME
import tournament.events.auth.util.nullIfBlank
import java.time.LocalDateTime
import java.util.*

/**
 * Merge the user info we collected with the ones provided by the third-party providers.
 *
 * - We apply the info collected by third-party providers (in [providerUserInfoList]) in chronological order.
 *   The chronological order is determined by the [ProviderUserInfo.updatedAt] field.
 *   The [ProviderUserInfo.changeDate] is used if the [ProviderUserInfo.updatedAt] field is null.
 *
 * - Then we apply the info we collected as a first-party that are stored in [collectedUserInfoList].
 *
 * - FIXME: Finally, we filter the user info that will be returned according to the [context].
 */
internal class UserInfoMerger(
    // private val user: User,
    private val userId: UUID,
    private val collectedUserInfoList: List<CollectedUserInfo>? = null,
    private val providerUserInfoList: List<ProviderUserInfo> = emptyList()
) {

    fun merge(): RawUserInfo {
        return RawUserInfoBuilder(userId).apply {
            merge(this)
        }.build()
    }

    internal fun merge(builder: RawUserInfoBuilder) {
        providerUserInfoList.sortedBy(::getUpdatedAt)
            .fold(builder, ::apply)
        collectedUserInfoList?.let { apply(builder, it) }
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
        getUpdatedAt(providerUserInfo).let(builder::withUpdateAt)
        return builder
    }

    internal fun apply(
        builder: RawUserInfoBuilder,
        collectedUserInfoList: List<CollectedUserInfo>
    ): RawUserInfoBuilder {
        var updatedAt: LocalDateTime? = null
        collectedUserInfoList.forEach { info ->
            when {
                info.claim.id == NAME && info.value is String -> builder.withName(info.value)
                info.claim.id == GIVEN_NAME && info.value is String -> builder.withGivenName(info.value)
                info.claim.id == FAMILY_NAME && info.value is String -> builder.withFamilyName(info.value)
                info.claim.id == MIDDLE_NAME && info.value is String -> builder.withMiddleName(info.value)
                info.claim.id == NICKNAME && info.value is String -> builder.withNickname(info.value)
                info.claim.id == PREFERRED_USERNAME && info.value is String -> builder.withPreferredUsername(info.value)
            }
            if (updatedAt == null || updatedAt?.isBefore(info.collectionDate) == true) {
                updatedAt = info.collectionDate
            }
        }
        /* FIXME
        if (collectedClaims.contains(PROFILE)) {
            builder.withProfile(info.profile)
        }
        if (collectedClaims.contains(PICTURE)) {
            builder.withPicture(info.picture)
        }
        if (collectedClaims.contains(WEBSITE)) {
            builder.withWebsite(info.website)
        }

        if (collectedClaims.contains(EMAIL)) {
            builder.withEmail(info.email, info.emailVerified)
        }

        if (collectedClaims.contains(GENDER)) {
            builder.withGender(info.gender)
        }
        if (collectedClaims.contains(BIRTH_DATE)) {
            builder.withBirthDate(info.birthDate)
        }

        if (collectedClaims.contains(ZONE_INFO)) {
            builder.withZoneInfo(info.zoneInfo)
        }
        if (collectedClaims.contains(LOCALE)) {
            builder.withLocale(info.locale)
        }

        if (collectedClaims.contains(PHONE_NUMBER)) {
            builder.withPhoneNumber(info.phoneNumber, info.phoneNumberVerified)
        }

         */
        return builder
    }
}
