package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.BIRTH_DATE
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.EMAIL
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.EMAIL_VERIFIED
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.FAMILY_NAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.GENDER
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.GIVEN_NAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.LOCALE
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.MIDDLE_NAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.NAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.NICKNAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.PHONE_NUMBER
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.PHONE_NUMBER_VERIFIED
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.PICTURE
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.PREFERRED_USERNAME
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.PROFILE
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.SUB
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.UPDATED_AT
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.WEBSITE
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.ZONE_INFO
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Serdeable
data class UserInfoResource(
    @get:Schema(
        description = "Identifier for the end-user.",
    )
    @get:JsonProperty(SUB)
    val sub: String,
    @get:Schema(
        description = "End-user's full name."
    )
    @get:JsonProperty(NAME)
    val name: String?,
    @get:Schema(
        description = "Given name(s) or first name(s) of the end-user."
    )
    @get:JsonProperty(GIVEN_NAME)
    val givenName: String?,
    @get:Schema(
        description = "Surname(s) or last name(s) of the end-user."
    )
    @get:JsonProperty(FAMILY_NAME)
    val familyName: String?,
    @get:Schema(
        description = "Middle name(s) of the end-user."
    )
    @get:JsonProperty(MIDDLE_NAME)
    val middleName: String?,
    @get:Schema(
        description = "Casual name of the end-user."
    )
    @get:JsonProperty(NICKNAME)
    val nickname: String?,
    @get:Schema(
        description = "Shorthand name of the end-user."
    )
    @get:JsonProperty(PREFERRED_USERNAME)
    val preferredUsername: String?,
    @get:Schema(
        description = "URL of the end-user's profile page."
    )
    @get:JsonProperty(PROFILE)
    val profile: String?,
    @get:Schema(
        description = "URL of the end-user's profile picture."
    )
    @get:JsonProperty(PICTURE)
    val picture: String?,
    @get:Schema(
        description = "URL of the end-user's Web page or blog."
    )
    @get:JsonProperty(WEBSITE)
    val website: String?,
    @get:Schema(
        description = "End-user's preferred e-mail address."
    )
    @get:JsonProperty(EMAIL)
    val email: String?,
    @get:Schema(
        description = "True if the end-user's e-mail address has been verified, otherwise false."
    )
    @get:JsonProperty(EMAIL_VERIFIED)
    val emailVerified: String?,
    @get:Schema(
        description = "End-user's gender."
    )
    @get:JsonProperty(GENDER)
    val gender: String?,
    @get:Schema(
        description = "End-user's birthday."
    )
    @get:JsonProperty(BIRTH_DATE)
    val birthDate: LocalDate?,
    @get:Schema(
        description = "String from IANA Time Zone Database representing the end-user's time zone."
    )
    @get:JsonProperty(ZONE_INFO)
    val zoneInfo: String?,
    @get:Schema(
        description = "End-user's locale, represented as a BCP47 language tag."
    )
    @get:JsonProperty(LOCALE)
    val locale: String?,
    @get:Schema(
        description = "End-user's preferred telephone number."
    )
    @get:JsonProperty(PHONE_NUMBER)
    val phoneNumber: String?,
    @get:Schema(
        description = "True if the end-user's phone number has been verified, otherwise false."
    )
    @get:JsonProperty(PHONE_NUMBER_VERIFIED)
    val phoneNumberVerified: Boolean?,
    @get:Schema(
        description = "End-user's preferred postal address."
    )
    @get:JsonProperty("address")
    val address: AddressResource?,
    @get:Schema(
        description = "Time the end-user's information was last updated. It is the number of seconds from epoch in UTC timezone."
    )
    @get:JsonProperty(UPDATED_AT)
    val updatedAt: Long?
)
