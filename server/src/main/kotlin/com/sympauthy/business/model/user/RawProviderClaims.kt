package com.sympauthy.business.model.user

import com.sympauthy.business.model.user.claim.OpenIdClaim
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
     */
    fun getClaimValueOrNull(claim: OpenIdClaim): String? = when (claim) {
        OpenIdClaim.SUBJECT -> subject
        OpenIdClaim.NAME -> name
        OpenIdClaim.GIVEN_NAME -> givenName
        OpenIdClaim.FAMILY_NAME -> familyName
        OpenIdClaim.MIDDLE_NAME -> middleName
        OpenIdClaim.NICKNAME -> nickname
        OpenIdClaim.PREFERRED_USERNAME -> preferredUsername
        OpenIdClaim.PROFILE -> profile
        OpenIdClaim.PICTURE -> picture
        OpenIdClaim.WEBSITE -> website
        OpenIdClaim.EMAIL -> email
        OpenIdClaim.GENDER -> gender
        OpenIdClaim.BIRTH_DATE -> birthDate?.toString()
        OpenIdClaim.ZONE_INFO -> zoneInfo
        OpenIdClaim.LOCALE -> locale
        OpenIdClaim.PHONE_NUMBER -> phoneNumber
        OpenIdClaim.STREET_ADDRESS -> streetAddress
        OpenIdClaim.LOCALITY -> locality
        OpenIdClaim.REGION -> region
        OpenIdClaim.POSTAL_CODE -> postalCode
        OpenIdClaim.COUNTRY -> country
        OpenIdClaim.UPDATED_AT -> updatedAt?.toString()
    }
}

class RawUserInfoBuilder(
    private val userId: UUID
) {
    private var name: String? = null
    private var givenName: String? = null
    private var familyName: String? = null
    private var middleName: String? = null
    private var nickname: String? = null
    private var preferredUsername: String? = null
    private var profile: String? = null
    private var picture: String? = null
    private var website: String? = null
    private var email: String? = null
    private var emailVerified: Boolean? = null
    private var gender: String? = null
    private var birthDate: LocalDate? = null
    private var zoneInfo: String? = null
    private var locale: String? = null
    private var phoneNumber: String? = null
    private var phoneNumberVerified: Boolean? = null
    private var streetAddress: String? = null
    private var locality: String? = null
    private var region: String? = null
    private var postalCode: String? = null
    private var country: String? = null
    private var updatedAt: LocalDateTime? = null

    fun withName(name: String?) = this.apply {
        this.name = name
    }

    fun withGivenName(givenName: String?) = this.apply {
        this.givenName = givenName
    }

    fun withFamilyName(familyName: String?) = this.apply {
        this.familyName = familyName
    }

    fun withMiddleName(middleName: String?) = this.apply {
        this.middleName = middleName
    }

    fun withNickname(nickname: String?) = this.apply {
        this.nickname = nickname
    }

    fun withPreferredUsername(preferredUsername: String?) = this.apply {
        this.preferredUsername = preferredUsername
    }

    fun withProfile(profile: String?) = this.apply {
        this.profile = profile
    }

    fun withPicture(picture: String?) = this.apply {
        this.picture = picture
    }

    fun withWebsite(website: String?) = this.apply {
        this.website = website
    }

    fun withEmail(email: String?, emailVerified: Boolean?) = this.apply {
        this.email = email
        this.emailVerified = emailVerified
    }

    fun withGender(gender: String?) = this.apply {
        this.gender = gender
    }

    fun withBirthDate(birthDate: LocalDate?) = this.apply {
        this.birthDate = birthDate
    }

    fun withZoneInfo(zoneInfo: String?) = this.apply {
        this.zoneInfo = zoneInfo
    }

    fun withLocale(locale: String?) = this.apply {
        this.locale = locale
    }

    fun withPhoneNumber(phoneNumber: String?, phoneNumberVerified: Boolean?) = this.apply {
        this.phoneNumber = phoneNumber
        this.phoneNumberVerified = phoneNumberVerified
    }

    fun withStreetAddress(streetAddress: String?) = this.apply {
        this.streetAddress = streetAddress
    }

    fun withLocality(locality: String?) = this.apply {
        this.locality = locality
    }

    fun withRegion(region: String?) = this.apply {
        this.region = region
    }

    fun withPostalCode(postalCode: String?) = this.apply {
        this.postalCode = postalCode
    }

    fun withCountry(country: String?) = this.apply {
        this.country = country
    }

    fun withUpdateAt(updatedAt: LocalDateTime?) = this.apply {
        this.updatedAt = updatedAt
    }

    fun build() = RawProviderClaims(
        subject = userId.toString(),

        name = name,
        givenName = givenName,
        familyName = familyName,
        middleName = middleName,
        nickname = nickname,

        preferredUsername = preferredUsername,
        profile = profile,
        picture = picture,
        website = website,

        email = email,
        emailVerified = emailVerified,

        gender = gender,
        birthDate = birthDate,

        zoneInfo = zoneInfo,
        locale = locale,

        phoneNumber = phoneNumber,
        phoneNumberVerified = phoneNumberVerified,

        streetAddress = streetAddress,
        locality = locality,
        region = region,
        postalCode = postalCode,
        country = country,

        updatedAt = updatedAt
    )
}
