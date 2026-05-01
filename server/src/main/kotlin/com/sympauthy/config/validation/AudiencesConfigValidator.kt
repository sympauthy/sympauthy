package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAudience
import com.sympauthy.config.properties.AudienceConfigurationProperties.Companion.AUDIENCES_KEY
import jakarta.inject.Singleton

@Singleton
class AudiencesConfigValidator {
    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedAudience>
    ): List<Audience> {
        val audiences = parsed.mapNotNull { it.toAudienceOrNull() }

        val duplicateIds = audiences.groupBy { it.id }
            .filter { it.value.size > 1 }
            .keys
        for (duplicateId in duplicateIds) {
            ctx.addError(
                configExceptionOf(
                    "$AUDIENCES_KEY.$duplicateId",
                    "config.audiences.duplicate_id",
                    "audience" to duplicateId
                )
            )
        }

        return audiences
    }

    private fun ParsedAudience.toAudienceOrNull(): Audience? {
        val id = this.id ?: return null
        return Audience(
            id = id,
            tokenAudience = tokenAudience ?: id,
            signUpEnabled = signUpEnabled ?: true,
            invitationEnabled = invitationEnabled ?: false
        )
    }
}
