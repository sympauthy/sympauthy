package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedUIConfig
import jakarta.inject.Singleton

@Singleton
class UIConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedUIConfig
    ) {
        // No additional validation beyond parsing for UIConfig.
    }
}
