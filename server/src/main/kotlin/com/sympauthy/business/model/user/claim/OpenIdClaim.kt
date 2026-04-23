package com.sympauthy.business.model.user.claim

/**
 * Generated OpenID Connect claims whose values are managed by the authorization server.
 *
 * These claims are always enabled, read-only, and not configurable beyond unconditional
 * client read scopes. All structural properties are hardcoded.
 */
enum class GeneratedOpenIdConnectClaim(
    val id: String,
    val verifiedId: String? = null,
    val dataType: ClaimDataType,
    val group: ClaimGroup? = null,
    val scope: String
) {
    SUBJECT(
        id = OpenIdConnectClaimId.SUB,
        dataType = ClaimDataType.STRING,
        scope = "profile"
    ),
    UPDATED_AT(
        id = OpenIdConnectClaimId.UPDATED_AT,
        dataType = ClaimDataType.STRING,
        scope = "profile"
    );
}

/**
 * Identifiers of all OpenID Connect claims supported by this application.
 *
 * Used to determine the [ClaimOrigin] of a claim: if its ID is listed here,
 * the claim originates from the OpenID Connect specification.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims">Standard claims</a>
 */
object OpenIdConnectClaimId {
    const val SUB = "sub"
    const val NAME = "name"
    const val GIVEN_NAME = "given_name"
    const val FAMILY_NAME = "family_name"
    const val MIDDLE_NAME = "middle_name"
    const val NICKNAME = "nickname"
    const val PREFERRED_USERNAME = "preferred_username"
    const val PROFILE = "profile"
    const val PICTURE = "picture"
    const val WEBSITE = "website"
    const val EMAIL = "email"
    const val EMAIL_VERIFIED = "email_verified"
    const val GENDER = "gender"
    const val BIRTH_DATE = "birth_date"
    const val ZONE_INFO = "zoneinfo"
    const val LOCALE = "locale"
    const val PHONE_NUMBER = "phone_number"
    const val PHONE_NUMBER_VERIFIED = "phone_number_verified"
    const val UPDATED_AT = "updated_at"
    const val STREET_ADDRESS = "street_address"
    const val LOCALITY = "locality"
    const val REGION = "region"
    const val POSTAL_CODE = "postal_code"
    const val COUNTRY = "country"

    val ALL: Set<String> = setOf(
        SUB, NAME, GIVEN_NAME, FAMILY_NAME, MIDDLE_NAME, NICKNAME,
        PREFERRED_USERNAME, PROFILE, PICTURE, WEBSITE, EMAIL, EMAIL_VERIFIED,
        GENDER, BIRTH_DATE, ZONE_INFO, LOCALE, PHONE_NUMBER, PHONE_NUMBER_VERIFIED,
        UPDATED_AT, STREET_ADDRESS, LOCALITY, REGION, POSTAL_CODE, COUNTRY
    )
}
