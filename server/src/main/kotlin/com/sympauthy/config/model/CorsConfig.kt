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
 * The enabled configuration, or null where the `cors` section is invalid.
 *
 * Unlike the other configuration models, [CorsConfig] has no `orThrow()`: it is read on every
 * preflight, which narrows the sealed type rather than throwing
 * (`docs/standards/config-layer-code-standard.md`) and falls back to the mandatory header set.
 */
fun CorsConfig.orNull(): EnabledCorsConfig? {
    return when (this) {
        is EnabledCorsConfig -> this
        is DisabledCorsConfig -> null
    }
}
