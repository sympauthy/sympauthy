package com.sympauthy.config.parsing

import com.sympauthy.business.model.flow.AuthorizationFlowType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties.Companion.AUTHORIZATION_FLOWS_KEY
import jakarta.inject.Singleton
import java.net.URI

data class ParsedAuthorizationFlow(
    val id: String,
    val type: AuthorizationFlowType?,
    val rootUri: URI?,
    val signInUri: URI?,
    val collectClaimsUri: URI?,
    val validateClaimsUri: URI?,
    val errorUri: URI?,
    val mfaUri: URI?,
    val mfaTotpEnrollUri: URI?,
    val mfaTotpChallengeUri: URI?
)

@Singleton
class AuthorizationFlowsConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<AuthorizationFlowConfigurationProperties>
    ): List<ParsedAuthorizationFlow> {
        return propertiesList.map { properties ->
            parseFlow(ctx, properties)
        }
    }

    private fun parseFlow(
        ctx: ConfigParsingContext,
        properties: AuthorizationFlowConfigurationProperties
    ): ParsedAuthorizationFlow {
        val configKeyPrefix = "$AUTHORIZATION_FLOWS_KEY.${properties.id}"
        val type = ctx.parse {
            parser.getEnumOrThrow<AuthorizationFlowConfigurationProperties, AuthorizationFlowType>(
                properties, "$configKeyPrefix.type",
                AuthorizationFlowConfigurationProperties::type
            )
        }
        val rootUri = ctx.parse {
            parser.getUri(properties, "$configKeyPrefix.root", AuthorizationFlowConfigurationProperties::root)
        }
        val signInUri = ctx.parse {
            parser.getUriOrThrow(properties, "$configKeyPrefix.sign-in", AuthorizationFlowConfigurationProperties::signIn)
        }
        val collectClaimsUri = ctx.parse {
            parser.getUriOrThrow(
                properties, "$configKeyPrefix.collect-claims",
                AuthorizationFlowConfigurationProperties::collectClaims
            )
        }
        val validateClaimsUri = ctx.parse {
            parser.getUriOrThrow(
                properties, "$configKeyPrefix.validate-claims",
                AuthorizationFlowConfigurationProperties::validateClaims
            )
        }
        val errorUri = ctx.parse {
            parser.getUriOrThrow(properties, "$configKeyPrefix.error", AuthorizationFlowConfigurationProperties::error)
        }
        val mfaUri = properties.mfa?.let {
            ctx.parse {
                parser.getUriOrThrow(properties, "$configKeyPrefix.mfa", AuthorizationFlowConfigurationProperties::mfa)
            }
        }
        val mfaTotpEnrollUri = properties.mfaTotpEnroll?.let {
            ctx.parse {
                parser.getUriOrThrow(
                    properties, "$configKeyPrefix.mfa-totp-enroll",
                    AuthorizationFlowConfigurationProperties::mfaTotpEnroll
                )
            }
        }
        val mfaTotpChallengeUri = properties.mfaTotpChallenge?.let {
            ctx.parse {
                parser.getUriOrThrow(
                    properties, "$configKeyPrefix.mfa-totp-challenge",
                    AuthorizationFlowConfigurationProperties::mfaTotpChallenge
                )
            }
        }
        return ParsedAuthorizationFlow(
            id = properties.id,
            type = type,
            rootUri = rootUri,
            signInUri = signInUri,
            collectClaimsUri = collectClaimsUri,
            validateClaimsUri = validateClaimsUri,
            errorUri = errorUri,
            mfaUri = mfaUri,
            mfaTotpEnrollUri = mfaTotpEnrollUri,
            mfaTotpChallengeUri = mfaTotpChallengeUri
        )
    }
}
