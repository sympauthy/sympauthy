package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId.ADDRESS
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

/**
 * What the published specification says about when a claim of the userinfo response is left out, on every
 * claim it declares.
 */
internal const val USERINFO_CLAIM_ABSENT_DESCRIPTION = " Absent when the user holds no value for it, the " +
    "end-user did not consent to share it, or the configuration does not publish it in the userinfo response."

@Schema(
    description = "Claims of the end-user the access token was issued for, OpenID Connect Core 1.0 §5.1. " +
        "Beside the properties declared here, the response carries the claims the configuration defines " +
        "beyond these, each under its identifier.",
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE
)
@Serdeable
data class UserInfoResource(
    @get:Schema(
        description = "Identifier of the user the access token was issued for.",
    )
    @get:JsonProperty(SUB)
    val sub: String,
    @get:Schema(
        description = "Full name of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(NAME)
    val name: String?,
    @get:Schema(
        description = "Given names of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(GIVEN_NAME)
    val givenName: String?,
    @get:Schema(
        description = "Family names of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(FAMILY_NAME)
    val familyName: String?,
    @get:Schema(
        description = "Middle names of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(MIDDLE_NAME)
    val middleName: String?,
    @get:Schema(
        description = "Casual name of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(NICKNAME)
    val nickname: String?,
    @get:Schema(
        description = "Shorthand name the end-user wishes to be referred to by." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(PREFERRED_USERNAME)
    val preferredUsername: String?,
    @get:Schema(
        description = "URL of the profile page of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(PROFILE)
    val profile: String?,
    @get:Schema(
        description = "URL of the profile picture of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(PICTURE)
    val picture: String?,
    @get:Schema(
        description = "URL of the web page or blog of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(WEBSITE)
    val website: String?,
    @get:Schema(
        description = "Preferred email address of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(EMAIL)
    val email: String?,
    @get:Schema(
        description = "Whether the email address of the end-user was verified. Absent when the user never held " +
            "one, the end-user did not consent to share it, or the configuration does not publish `email` in " +
            "the userinfo response."
    )
    @get:JsonProperty(EMAIL_VERIFIED)
    val emailVerified: Boolean?,
    @get:Schema(
        description = "Gender of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(GENDER)
    val gender: String?,
    @get:Schema(
        description = "Birthday of the end-user, as an ISO 8601 date." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(BIRTH_DATE)
    val birthDate: LocalDate?,
    @get:Schema(
        description = "Time zone of the end-user, from the IANA time zone database, for example " +
            "`Europe/Paris`." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(ZONE_INFO)
    val zoneInfo: String?,
    @get:Schema(
        description = "Locale of the end-user, as a BCP 47 language tag, for example `fr-FR`." +
            USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(LOCALE)
    val locale: String?,
    @get:Schema(
        description = "Preferred telephone number of the end-user." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty(PHONE_NUMBER)
    val phoneNumber: String?,
    @get:Schema(
        description = "Whether the telephone number of the end-user was verified. Absent when the user never " +
            "held one, the end-user did not consent to share it, or the configuration does not publish " +
            "`phone_number` in the userinfo response."
    )
    @get:JsonProperty(PHONE_NUMBER_VERIFIED)
    val phoneNumberVerified: Boolean?,
    @get:Schema(
        description = "Preferred postal address of the end-user. Absent when none of its members would be " +
            "sent."
    )
    @get:JsonProperty(ADDRESS)
    val address: AddressResource?,
    @get:Schema(
        description = "Time at which a claim value of the user was last collected, in seconds since the Unix " +
            "epoch. Absent when none was ever collected."
    )
    @get:JsonProperty(UPDATED_AT)
    val updatedAt: Long?,
    /**
     * The claims this resource declares no property of its own for, each under the identifier it was
     * configured with, serialized beside the properties above rather than under a property of their own.
     *
     * Hidden from the schema because it is not a property a client ever reads: what it holds is declared on
     * the class as additional properties, which is what the response actually carries.
     */
    @get:Schema(hidden = true)
    @get:JsonAnyGetter
    val additionalClaims: Map<String, Any>
) {
    companion object {
        /**
         * The JSON properties this resource declares, and therefore the names [additionalClaims] may not
         * be written under: a value published twice under one name is a response whose reader picks.
         */
        val DECLARED_PROPERTIES: Set<String> = OpenIdConnectClaimId.ALL + ADDRESS
    }
}
