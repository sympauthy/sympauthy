package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Serdeable
data class AddressResource(
    @get:Schema(
        description = "Full mailing address, formatted for display."
    )
    @get:JsonProperty("formatted")
    val formatted: String?,
    @get:Schema(
        description = "Full street address component, which MAY include house number, street name, Post Office Box, " +
                "and multi-line extended street address information."
    )
    @get:JsonProperty("street_address")
    val streetAddress: String?,
    @get:Schema(
        description = "City or locality component."
    )
    @get:JsonProperty("locality")
    val locality: String?,
    @get:Schema(
        description = "State, province, prefecture, or region component."
    )
    @get:JsonProperty("region")
    val region: String?,
    @get:Schema(
        description = "Zip code or postal code component."
    )
    @get:JsonProperty("postal_code")
    val postalCode: String?,
    @get:Schema(
        description = "Country name component."
    )
    @get:JsonProperty("country")
    val country: String?
)
