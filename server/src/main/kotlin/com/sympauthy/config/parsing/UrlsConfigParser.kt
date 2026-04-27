package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.UrlsConfigurationProperties
import com.sympauthy.config.properties.UrlsConfigurationProperties.Companion.URLS_KEY
import jakarta.inject.Singleton
import java.net.URI

data class ParsedUrlsConfig(
    val root: URI?
)

@Singleton
class UrlsConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: UrlsConfigurationProperties
    ): ParsedUrlsConfig {
        val root = ctx.parse {
            parser.getAbsoluteUriOrThrow(properties, "$URLS_KEY.root", UrlsConfigurationProperties::root)
        }
        return ParsedUrlsConfig(root = root)
    }
}
