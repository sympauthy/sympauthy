package com.sympauthy.view

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

/**
 * What `/branding/brand.json` answers: not a contract of the API, since `/branding` is not one of its surfaces,
 * but written by its JSON rules.
 */
@Serdeable
data class BrandResource(
    @get:JsonProperty("display_name")
    val displayName: String?,
    @get:JsonProperty("dark_mode")
    val darkMode: String
)
