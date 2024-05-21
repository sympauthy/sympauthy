package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException
import io.micronaut.http.uri.UriBuilder
import java.net.URI

sealed class UrlsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledUrlsConfig(
    val root: URI,
    val flow: FlowUrlConfig
) : UrlsConfig()

class DisabledUrlsConfig(
    configurationErrors: List<ConfigurationException>
) : UrlsConfig(configurationErrors)

fun UrlsConfig.getOrNull(): EnabledUrlsConfig? {
    return when (this) {
        is EnabledUrlsConfig -> this
        is DisabledUrlsConfig -> null
    }
}

fun UrlsConfig.orThrow(): EnabledUrlsConfig {
    return this.getOrNull() ?: throw this.invalidConfig
}

data class FlowUrlConfig(
    /**
     * Page allowing the user either to:
     * - authenticate by entering its credentials
     * - select a third-party provider that will authenticate him.
     */
    val signIn: URI,
    /**
     * Page in charge of collecting claims from the end-user claims.
     *
     * This page will be presented during the authentication flow whenever all required claims
     * are not collected for an end-user.
     */
    val collectClaims: URI,
    /**
     * Page displaying
     */
    val error: URI
) {
    /**
     * Return all URIs that are part of the end-user flow.
     */
    val all: List<URI>
        get() = listOf(signIn, error)
}

fun UrlsConfig.buildUponRoot(): UriBuilder {
    return UriBuilder.of(orThrow().root)
}

/**
 * Return an [UriBuilder] configured with:
 * - the root URI
 * - appended with the provided [path]
 *
 * Path variables will be replaced by their values if they are provided in the [pathParams] pairs.
 */
fun EnabledUrlsConfig.getUri(path: String, vararg pathParams: Pair<String, String>): URI {
    val replacedPath = pathParams.fold(path) { acc, (pathParam, value) ->
        acc.replace("{${pathParam}}", value)
    }
    return buildUponRoot()
        .path(replacedPath)
        .build()
}
