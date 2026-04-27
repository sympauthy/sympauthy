package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedAdminConfig
import jakarta.inject.Singleton

@Singleton
class AdminConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAdminConfig
    ) {
        // No additional validation beyond parsing for AdminConfig.
    }
}
