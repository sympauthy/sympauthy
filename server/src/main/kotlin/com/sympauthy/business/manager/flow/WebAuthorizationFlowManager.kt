package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URISyntaxException

/**
 * Manager providing utility methods supporting the lifecycle of web-based interactive authorization flows described in the
 * OAuth 2 like:
 * - [Authorization Code Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1)
 * - [Implicit Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.2)
 *
 * This manager is responsible for:
 * - creating an [AuthorizeAttempt] using one of the grant types cited above after validation.
 * - verifying the state of an [AuthorizeAttempt].
 */
@Singleton
class WebAuthorizationFlowManager(
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val claimValidationManager: WebAuthorizationFlowClaimValidationManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val uncheckedClientTemplatesConfig: Flow<ClientTemplatesConfig>
) {

    /**
     * Return the default [WebAuthorizationFlow].
     *
     * First checks the default client template for an authorization flow, then falls back
     * to the hardcoded default web authorization flow.
     */
    suspend fun getDefaultWebAuthorizationFlow(): WebAuthorizationFlow {
        val templateFlow = uncheckedClientTemplatesConfig.first().orThrow()
            .templates[DEFAULT]?.authorizationFlow
        if (templateFlow is WebAuthorizationFlow) {
            return templateFlow
        }
        return authorizationFlowManager.defaultWebAuthorizationFlow
    }

    /**
     * Return the [WebAuthorizationFlow] identified by [id].
     * Return null if not found or if not of type [WebAuthorizationFlow].
     */
    fun findByIdOrNull(id: String?): WebAuthorizationFlow? {
        val authorizationFlow = id?.let(authorizationFlowManager::findByIdOrNull)
        return authorizationFlow as? WebAuthorizationFlow
    }

    /**
     * Return the [WebAuthorizationFlow] identified by [id].
     * Otherwise, throw an unrecoverable [BusinessException] with detailsId ```flow.web.invalid_flow```.
     */
    fun findById(id: String?): WebAuthorizationFlow {
        return findByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "flow.web.invalid_flow",
            "flowId" to (id ?: "null")
        )
    }

    /**
     * Create a new [AuthorizeAttempt] for the end-user
     *
     * This method is in charge of:
     * - validating the [uncheckedClientId]. Redirect the user to the error page of the default web authorization flow in case of error.
     * - selecting the flow: Either the one provided by the [Client] or the default one.
     * - creating the [AuthorizeAttempt].
     * - validating the [uncheckedScopes]. Redirect the user to the error page of the selected flow in case of error.
     * - validating the [uncheckedRedirectUri]. Redirect the user to the error page of the selected flow in case of error.
     *
     * Parameters are expected to be non-validated as this method will perform the validation and assign default values
     * if necessary.
     */
    suspend fun startAuthorizationWith(
        uncheckedClientId: String?,
        uncheckedClientState: String?,
        uncheckedClientNonce: String?,
        uncheckedScopes: String?,
        uncheckedRedirectUri: String?,
        uncheckedCodeChallenge: String? = null,
        uncheckedCodeChallengeMethod: String? = null
    ): Pair<AuthorizeAttempt, WebAuthorizationFlow> {
        val (client, clientException) = try {
            val client = clientManager.parseRequestedClient(uncheckedClientId)
            if (!client.supportsGrantType(GrantType.AUTHORIZATION_CODE)) {
                throw BusinessException(
                    recoverable = false,
                    detailsId = "authorize.unauthorized_grant_type",
                    descriptionId = "description.authorize.unauthorized_grant_type"
                )
            }
            client to null
        } catch (e: BusinessException) {
            null to e
        }

        val authorizationFlowId = client?.authorizationFlow?.id
        val defaultFlow = getDefaultWebAuthorizationFlow()
        val (flow, flowException) = if (authorizationFlowId != null) {
            try {
            findById(authorizationFlowId) to null
            } catch (e: BusinessException) {
                defaultFlow to e
            }
        } else {
            defaultFlow to null
        }

        val (scopes, scopeException) = if (client != null) {
            try {
                scopeManager.parseRequestedScopes(
                    client = client,
                    uncheckedScopes = uncheckedScopes
                ) to null
            } catch (e: BusinessException) {
                emptyList<Scope>() to e
            }
        } else {
            emptyList<Scope>() to null
        }

        val (redirectUri, redirectUriException) = if (client != null) {
            try {
                parseRequestedRedirectUri(
                    client = client,
                    uncheckedRedirectUri = uncheckedRedirectUri
                ) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val (codeChallenge, codeChallengeMethod, pkceException) = parseCodeChallenge(
            client = client,
            uncheckedCodeChallenge = uncheckedCodeChallenge,
            uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod
        )

        val authorizeAttempt = authorizeAttemptManager.newAuthorizeAttempt(
            client = client,
            clientState = uncheckedClientState,
            authorizationFlow = flow,
            scopes = scopes,
            redirectUri = redirectUri,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            error = listOfNotNull(clientException, flowException, scopeException, redirectUriException, pkceException).firstOrNull()
        )
        return authorizeAttempt to flow
    }

    /**
     * Parses and validates PKCE parameters (RFC 7636).
     *
     * - If `code_challenge` is present with no method, defaults to `S256`.
     * - If the method is provided but not recognized, returns an error.
     * - If `code_challenge` is missing, returns an error (PKCE is required for all clients per OAuth 2.1).
     */
    internal fun parseCodeChallenge(
        client: Client?,
        uncheckedCodeChallenge: String?,
        uncheckedCodeChallengeMethod: String?
    ): Triple<String?, CodeChallengeMethod?, BusinessException?> {
        if (uncheckedCodeChallenge.isNullOrBlank()) {
            return Triple(
                null, null, BusinessException(
                    recoverable = false,
                    detailsId = "authorize.pkce.missing_code_challenge",
                    descriptionId = "description.authorize.pkce.missing_code_challenge"
                )
            )
        }

        val method = if (uncheckedCodeChallengeMethod.isNullOrBlank()) {
            CodeChallengeMethod.S256
        } else {
            CodeChallengeMethod.fromValueOrNull(uncheckedCodeChallengeMethod)
                ?: return Triple(
                    null, null, BusinessException(
                        recoverable = false,
                        detailsId = "authorize.pkce.unsupported_method",
                        descriptionId = "description.authorize.pkce.unsupported_method",
                        values = mapOf("method" to uncheckedCodeChallengeMethod)
                    )
                )
        }

        return Triple(uncheckedCodeChallenge, method, null)
    }

    /**
     * Parses and validates the requested redirect URI provided by the end-user in the authorization request.
     *
     * This method ensures that the provided redirect URI is valid and authorized for the given [client].
     * If the provided URI is null, it may default to a pre-configured value, based on the client's settings.
     */
    fun parseRequestedRedirectUri(
        client: Client,
        uncheckedRedirectUri: String?
    ): URI {
        if (uncheckedRedirectUri.isNullOrBlank()) {
            throw BusinessException(
                recoverable = false,
                detailsId = "flow.web.parse_requested_redirect_uri.missing",
                descriptionId = "description.flow.web.parse_requested_redirect_uri.missing"
            )
        }
        val redirectURI = try {
            URI(uncheckedRedirectUri)
        } catch (e: URISyntaxException) {
            throw BusinessException(
                recoverable = false,
                detailsId = "flow.web.parse_requested_redirect_uri.invalid",
                descriptionId = "description.flow.web.parse_requested_redirect_uri.invalid",
                values = mapOf("redirect_uri" to uncheckedRedirectUri, "message" to (e.message ?: ""))
            )
        }

        if (!matchesAllowedRedirectUri(uncheckedRedirectUri, client.allowedRedirectUris)) {
            throw BusinessException(
                recoverable = false,
                detailsId = "flow.web.parse_requested_redirect_uri.not_allowed",
                descriptionId = "description.flow.web.parse_requested_redirect_uri.not_allowed",
                values = mapOf("redirect_uri" to uncheckedRedirectUri)
            )
        }
        return redirectURI
    }

    /**
     * Check if [requestedUri] matches any of the [allowedUris] using exact string matching
     * per OAuth 2.1 (section 7.5.3), with loopback port flexibility per RFC 8252.
     *
     * Loopback flexibility: for redirect URIs using `127.0.0.1` or `::1` with `http`/`https`,
     * the port is ignored during matching. This does NOT apply to `localhost`.
     */
    internal fun matchesAllowedRedirectUri(requestedUri: String, allowedUris: List<String>): Boolean {
        // 1. Exact string match (OAuth 2.1 compliance)
        if (allowedUris.contains(requestedUri)) return true

        // 2. Loopback port flexibility (RFC 8252)
        val parsedRequested = try {
            URI(requestedUri)
        } catch (_: URISyntaxException) {
            return false
        }
        val requestedScheme = parsedRequested.scheme ?: return false
        if (requestedScheme != "http" && requestedScheme != "https") return false
        val requestedHost = parsedRequested.host ?: return false
        if (requestedHost != "127.0.0.1" && requestedHost != "[::1]") return false

        return allowedUris.any { allowedUri ->
            val parsedAllowed = try {
                URI(allowedUri)
            } catch (_: URISyntaxException) {
                return@any false
            }
            parsedAllowed.scheme == requestedScheme &&
                parsedAllowed.host == requestedHost &&
                parsedAllowed.path == parsedRequested.path &&
                parsedAllowed.query == parsedRequested.query &&
                parsedAllowed.fragment == parsedRequested.fragment
        }
    }

    /**
     * Return the status of the [authorizeAttempt] if the end-user is going through a web authorization flow.
     */
    suspend fun getStatus(
        authorizeAttempt: AuthorizeAttempt
    ): WebAuthorizationFlowStatus {
        return when (authorizeAttempt) {
            is FailedAuthorizeAttempt -> getStatusForFailedAuthorizeAttempt()
            is CompletedAuthorizeAttempt -> getStatusForCompletedAuthorizeAttempt()
            is OnGoingAuthorizeAttempt -> getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)
        }
    }

    /**
     * Return the status of an attempt the authorization flow has failed.
     */
    internal fun getStatusForFailedAuthorizeAttempt(): WebAuthorizationFlowStatus {
        return WebAuthorizationFlowStatus(failed = true)
    }

    /**
     * Return the status of the [authorizeAttempt] if the end-user is going through a web authorization flow.
     */
    internal suspend fun getStatusForOnGoingAuthorizeAttempt(
        authorizeAttempt: OnGoingAuthorizeAttempt
    ): WebAuthorizationFlowStatus {
        val consentedScopes = authorizeAttempt.consentedScopes ?: emptyList()
        val identifierClaims = authorizeAttempt.userId?.let {
            collectedClaimManager.findIdentifierByUserId(it)
        } ?: emptyList()
        val consentedClaims = authorizeAttempt.userId?.let {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(it, consentedScopes)
        } ?: emptyList()
        val missingUser = authorizeAttempt.userId == null
        val missingMfa = uncheckedMfaConfig.orThrow().enabled && !authorizeAttempt.mfaPassed
        val allClaims = (identifierClaims + consentedClaims).distinctBy { it.claim.id }
        val missingRequiredClaims = !consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
            allClaims, consentedScopes
        )
        val missingMediaForClaimValidation = claimValidationManager.getReasonsToSendValidationCode(
            identifierClaims = identifierClaims,
            consentedClaims = consentedClaims
        )
            .map(ValidationCodeReason::media)
            .distinct()

        return WebAuthorizationFlowStatus(
            identifierClaims = identifierClaims,
            consentedClaims = consentedClaims,
            missingUser = missingUser,
            missingMfa = missingMfa,
            missingRequiredClaims = missingRequiredClaims,
            missingMediaForClaimValidation = missingMediaForClaimValidation
        )
    }

    /**
     * Return the status of an attempt if the end-user has completed the authorization flow.
     */
    internal fun getStatusForCompletedAuthorizeAttempt(): WebAuthorizationFlowStatus {
        return WebAuthorizationFlowStatus()
    }

    /**
     * Completes the authorization flow by calling [AuthorizationFlowManager.completeAuthorization]
     * if the [status] indicates that the flow is complete. Then return the updated [AuthorizeAttempt].
     */
    suspend fun completeIfNecessary(
        authorizeAttempt: AuthorizeAttempt,
        status: WebAuthorizationFlowStatus
    ): AuthorizeAttempt {
        return if (status.complete) {
            authorizationFlowManager.completeAuthorization(
                authorizeAttempt = authorizeAttempt
            )
        } else authorizeAttempt
    }

    suspend fun getStatusAndCompleteIfNecessary(
        authorizeAttempt: AuthorizeAttempt
    ): Pair<AuthorizeAttempt, WebAuthorizationFlowStatus> {
        val status = getStatus(authorizeAttempt)
        val completedAuthorizeAttempt = completeIfNecessary(
            authorizeAttempt = authorizeAttempt,
            status = status
        )
        return completedAuthorizeAttempt to status
    }
}

