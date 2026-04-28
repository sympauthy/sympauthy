package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAdminConfig
import jakarta.inject.Singleton

@Singleton
class AdminConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAdminConfig,
        audiencesById: Map<String, Audience>
    ) {
        if (parsed.enabled != true) return

        if (parsed.audience == null) {
            ctx.addError(configExceptionOf("admin.audience", "config.admin.audience.missing"))
        } else {
            validateAudienceId(
                ctx, parsed.audience, audiencesById,
                "admin.audience", "config.admin.audience.not_found"
            )
        }
    }
}
