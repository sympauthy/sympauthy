package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Postal address of the end-user, OpenID Connect Core 1.0 §5.1.1.")
@Serdeable
data class AddressResource(
    @get:Schema(
        description = "Full mailing address for display, built from the members below: the street address, " +
            "then the locality, region and postal code, then the country, each on its own line."
    )
    @get:JsonProperty("formatted")
    val formatted: String?,
    @get:Schema(
        description = "Street address, which may include the house number, the street name, a post office box " +
            "and several lines." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty("street_address")
    val streetAddress: String?,
    @get:Schema(
        description = "City or locality." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty("locality")
    val locality: String?,
    @get:Schema(
        description = "State, province, prefecture or region." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty("region")
    val region: String?,
    @get:Schema(
        description = "Postal code." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty("postal_code")
    val postalCode: String?,
    @get:Schema(
        description = "Country." + USERINFO_CLAIM_ABSENT_DESCRIPTION
    )
    @get:JsonProperty("country")
    val country: String?
)
