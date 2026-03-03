package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.AuthorizationFlowType
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties.Companion.AUTHORIZATION_FLOWS_KEY
import com.sympauthy.util.mergeUri
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

@Factory
class AuthorizationFlowsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    @Singleton
    fun provideAuthorizationFlows(
        propertiesList: List<AuthorizationFlowConfigurationProperties>
    ): AuthorizationFlowsConfig {
        val errors = mutableListOf<ConfigurationException>()

        val flows = propertiesList.mapNotNull {
            provideAuthenticationFlow(
                properties = it,
                errors = errors
            )
        }

        return if (errors.isEmpty()) {
            EnabledAuthorizationFlowsConfig(
                flows = flows
            )
        } else {
            DisabledAuthorizationFlowsConfig(
                configurationErrors = errors
            )
        }
    }

    private fun provideAuthenticationFlow(
        properties: AuthorizationFlowConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): AuthorizationFlow? {
        val type = try {
            parser.getEnumOrThrow<AuthorizationFlowConfigurationProperties, AuthorizationFlowType>(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.type",
                AuthorizationFlowConfigurationProperties::type
            )
        } catch (e: ConfigurationException) {
            errors.add(e)
            return null
        }

        return when (type) {
            AuthorizationFlowType.WEB -> provideWebAuthenticationFlow(
                properties = properties,
                errors = errors
            )
        }
    }

    private fun provideWebAuthenticationFlow(
        properties: AuthorizationFlowConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): AuthorizationFlow? {
        val flowErrors = mutableListOf<ConfigurationException>()

        val rootUri = try {
            parser.getUri(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.root",
                AuthorizationFlowConfigurationProperties::root
            ) ?: uncheckedUrlsConfig.getOrNull()?.root
        } catch (e: ConfigurationException) {
            flowErrors.add(e)
            null
        }

        val signInUri = try {
            getWebAuthenticationFlowUri(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.sign-in", rootUri,
                AuthorizationFlowConfigurationProperties::signIn
            )
        } catch (e: ConfigurationException) {
            flowErrors.add(e)
            null
        }

        val collectClaimsUri = try {
            getWebAuthenticationFlowUri(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.collect-claims", rootUri,
                AuthorizationFlowConfigurationProperties::collectClaims
            )
        } catch (e: ConfigurationException) {
            flowErrors.add(e)
            null
        }

        val validateClaimsUri = try {
            getWebAuthenticationFlowUri(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.validate-claims", rootUri,
                AuthorizationFlowConfigurationProperties::validateClaims
            )
        } catch (e: ConfigurationException) {
            flowErrors.add(e)
            null
        }

        val errorUri = try {
            getWebAuthenticationFlowUri(
                properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.error", rootUri,
                AuthorizationFlowConfigurationProperties::error
            )
        } catch (e: ConfigurationException) {
            flowErrors.add(e)
            null
        }

        val mfaUri = properties.mfa?.let {
            try {
                getWebAuthenticationFlowUri(
                    properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.mfa", rootUri,
                    AuthorizationFlowConfigurationProperties::mfa
                )
            } catch (e: ConfigurationException) {
                flowErrors.add(e)
                null
            }
        }

        val mfaTotpEnrollUri = properties.mfaTotpEnroll?.let {
            try {
                getWebAuthenticationFlowUri(
                    properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.mfa-totp-enroll", rootUri,
                    AuthorizationFlowConfigurationProperties::mfaTotpEnroll
                )
            } catch (e: ConfigurationException) {
                flowErrors.add(e)
                null
            }
        }

        val mfaTotpChallengeUri = properties.mfaTotpChallenge?.let {
            try {
                getWebAuthenticationFlowUri(
                    properties, "$AUTHORIZATION_FLOWS_KEY.${properties.id}.mfa-totp-challenge", rootUri,
                    AuthorizationFlowConfigurationProperties::mfaTotpChallenge
                )
            } catch (e: ConfigurationException) {
                flowErrors.add(e)
                null
            }
        }

        return if (flowErrors.isEmpty()) {
            WebAuthorizationFlow(
                id = properties.id,
                signInUri = signInUri!!,
                collectClaimsUri = collectClaimsUri!!,
                validateClaimsUri = validateClaimsUri!!,
                errorUri = errorUri!!,
                mfaUri = mfaUri,
                mfaTotpEnrollUri = mfaTotpEnrollUri,
                mfaTotpChallengeUri = mfaTotpChallengeUri,
            )
        } else {
            errors.addAll(flowErrors)
            null
        }
    }

    private fun getWebAuthenticationFlowUri(
        properties: AuthorizationFlowConfigurationProperties,
        key: String,
        rootUri: URI?,
        value: (AuthorizationFlowConfigurationProperties) -> String?
    ): URI {
        val uri = parser.getUriOrThrow(properties, key, value)
        return rootUri?.let { mergeUri(it, uri) } ?: uri
    }
}
