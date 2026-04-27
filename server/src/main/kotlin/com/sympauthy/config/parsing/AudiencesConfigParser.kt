package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.properties.AudienceConfigurationProperties
import com.sympauthy.config.properties.AudienceConfigurationProperties.Companion.AUDIENCES_KEY
import jakarta.inject.Singleton

data class ParsedAudience(
    val id: String?,
    val tokenAudience: String?
)

@Singleton
class AudiencesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<AudienceConfigurationProperties>
    ): List<ParsedAudience> {
        if (propertiesList.isEmpty()) {
            ctx.addError(configExceptionOf(AUDIENCES_KEY, "config.audiences.empty"))
            return emptyList()
        }
        return propertiesList.map { properties ->
            parseAudience(ctx, properties)
        }
    }

    private fun parseAudience(
        ctx: ConfigParsingContext,
        properties: AudienceConfigurationProperties
    ): ParsedAudience {
        val subCtx = ctx.child()
        val id = subCtx.parse {
            parser.getStringOrThrow(
                properties,
                "$AUDIENCES_KEY.${properties.id}",
                AudienceConfigurationProperties::id
            )
        }
        val tokenAudience = subCtx.parse {
            parser.getString(
                properties,
                "$AUDIENCES_KEY.${properties.id}.token-audience",
                AudienceConfigurationProperties::tokenAudience
            )
        }
        ctx.merge(subCtx)
        return ParsedAudience(id = id, tokenAudience = tokenAudience)
    }
}
