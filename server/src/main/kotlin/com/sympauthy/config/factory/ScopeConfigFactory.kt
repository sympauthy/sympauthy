package com.sympauthy.config.factory

import com.sympauthy.business.model.user.isStandardScope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ScopeConfigurationProperties
import com.sympauthy.config.properties.ScopeConfigurationProperties.Companion.SCOPES_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ScopeConfigFactory(
    @Inject private val parser: ConfigParser
) {

    @Singleton
    fun provideScopes(
        propertiesList: List<ScopeConfigurationProperties>
    ): ScopesConfig {
        val errors = mutableListOf<ConfigurationException>()
        val scopes = propertiesList.mapNotNull { properties ->
            if (properties.id.isStandardScope()) {
                getStandardScope(properties = properties, errors = errors)
            } else {
                getCustomScope(properties = properties, errors = errors)
            }
        }

        return if (errors.isEmpty()) {
            EnabledScopesConfig(scopes)
        } else {
            DisabledScopesConfig(errors)
        }
    }

    private fun getStandardScope(
        properties: ScopeConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ScopeConfig? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val enabled = try {
            parser.getBoolean(
                properties, "$SCOPES_KEY.${properties.id}.enabled",
                ScopeConfigurationProperties::enabled
            ) ?: true
        } catch (e: ConfigurationException) {
            scopeErrors.add(e)
            null
        }

        return if (scopeErrors.isEmpty()) {
            StandardScopeConfig(
                scope = properties.id,
                enabled = enabled!!
            )
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }

    private fun getCustomScope(
        properties: ScopeConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ScopeConfig? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        return if (scopeErrors.isEmpty()) {
            CustomScopeConfig(
                scope = properties.id
            )
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }
}
