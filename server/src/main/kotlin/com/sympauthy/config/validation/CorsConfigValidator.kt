package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedCorsConfig
import com.sympauthy.config.properties.CorsConfigurationProperties.Companion.CORS_KEY
import jakarta.inject.Singleton

@Singleton
class CorsConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedCorsConfig
    ) {
        parsed.allowedHeaders.forEachIndexed { index, header ->
            // Errors are keyed per entry: the readiness indicator collapses errors sharing a key, so a
            // section-wide key would report only one of several invalid headers.
            val key = "$CORS_KEY.allowed-headers[$index]"
            when {
                header == WILDCARD -> ctx.addError(
                    configExceptionOf(key, "config.cors.allowed_headers.wildcard_unsupported")
                )

                !TOKEN.matches(header) -> ctx.addError(
                    configExceptionOf(key, "config.cors.allowed_headers.invalid", "header" to header)
                )
            }
        }
    }

    companion object {
        /**
         * The wildcard needs its own check: it is a valid [TOKEN], so the regex below does not reject it.
         * It is refused because `Access-Control-Allow-Headers: *` does not cover the `Authorization` header
         * per the Fetch specification, which makes it misleading rather than permissive.
         */
        private const val WILDCARD = "*"

        /**
         * A header name is a `field-name`, i.e. a `token`, as defined by
         * [RFC 9110](https://datatracker.ietf.org/doc/html/rfc9110#name-tokens).
         *
         * Enforcing it rejects the likeliest mistake — declaring several headers in a single entry, as in
         * `- "X-Foo, X-Bar"` — and prevents a separator or a CR/LF from being smuggled into the
         * `Access-Control-Allow-Headers` response header.
         */
        private val TOKEN = Regex("""[!#${'$'}%&'*+\-.^_`|~0-9A-Za-z]+""")
    }
}
