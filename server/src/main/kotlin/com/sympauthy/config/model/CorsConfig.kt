package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class CorsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledCorsConfig(
    /**
     * Additional header names advertised in the `Access-Control-Allow-Headers` response header.
     *
     * This list does NOT contain the mandatory headers (`Content-Type`, `Authorization` and `DPoP`): those
     * are hardcoded in com.sympauthy.api.filter.CorsPreflightHeaders and are always advertised, whatever
     * this configuration contains.
     */
    val allowedHeaders: List<String>
) : CorsConfig()

class DisabledCorsConfig(
    configurationErrors: List<ConfigurationException>
) : CorsConfig(configurationErrors)

/**
 * Unlike the other configuration models, [CorsConfig] deliberately exposes no `orThrow()` counterpart:
 * an invalid CORS configuration must never make a request fail.
 *
 * When the `cors` section is invalid, callers fall back to the mandatory header set and the errors are
 * surfaced through the readiness health indicator instead.
 */
fun CorsConfig.orNull(): EnabledCorsConfig? {
    return when (this) {
        is EnabledCorsConfig -> this
        is DisabledCorsConfig -> null
    }
}
