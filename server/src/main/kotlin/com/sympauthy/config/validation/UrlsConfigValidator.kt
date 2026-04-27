package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedUrlsConfig
import jakarta.inject.Singleton

@Singleton
class UrlsConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedUrlsConfig
    ) {
        // No additional validation beyond parsing for UrlsConfig.
        // The URI is validated as absolute during parsing by ConfigParser.getAbsoluteUriOrThrow.
    }
}
