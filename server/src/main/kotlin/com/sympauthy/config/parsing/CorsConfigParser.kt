package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.CorsConfigurationProperties
import com.sympauthy.config.properties.CorsConfigurationProperties.Companion.CORS_KEY
import jakarta.inject.Singleton

data class ParsedCorsConfig(
    val allowedHeaders: List<String>
)

@Singleton
class CorsConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: CorsConfigurationProperties
    ): ParsedCorsConfig {
        val allowedHeaders = properties.allowedHeaders
            ?.mapIndexedNotNull { index, value ->
                val key = "$CORS_KEY.allowed-headers[$index]"
                ctx.parse { parser.getStringOrThrow(properties, key) { value } }?.trim()
            }
            ?: emptyList()
        return ParsedCorsConfig(allowedHeaders = allowedHeaders)
    }
}
