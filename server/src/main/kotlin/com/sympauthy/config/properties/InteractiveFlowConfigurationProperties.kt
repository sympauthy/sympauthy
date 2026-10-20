package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthConfigurationProperties.Companion.AUTH_KEY
import com.sympauthy.config.properties.InteractiveFlowConfigurationProperties.Companion.INTERACTIVE_FLOW_KEY
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Nullable

@ConfigurationProperties(INTERACTIVE_FLOW_KEY)
interface InteractiveFlowConfigurationProperties {
    @get:Nullable
    val expiration: String?

    companion object {
        const val INTERACTIVE_FLOW_KEY = "$AUTH_KEY.interactive-flow"
    }
}
