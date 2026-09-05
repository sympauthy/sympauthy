package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Manager providing utility methods supporting the lifecycle of interactive auth flows described in the
 * OAuth 2 like:
 * - [Authorization Code Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.1)
 * - [Implicit Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.2)
 *
 * This manager is responsible for:
 * - creating an [InteractiveFlowSession] using one of the grant types cited above after validation.
 * - verifying the state of an [InteractiveFlowSession].
 */
@Singleton
class InteractiveAuthFlowSessionManager(
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val clientManager: ClientManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val uncheckedClientTemplatesConfig: Flow<ClientTemplatesConfig>
) {

    /**
     * Return the default [InteractiveFlow].
     *
     * First checks the default client template for an authorization flow, then falls back
     * to the hardcoded default interactive auth flow.
     */
    suspend fun getDefaultInteractiveFlow(): InteractiveFlow {
        val templateFlow = uncheckedClientTemplatesConfig.orThrow()
            .templates[DEFAULT]?.authorizationFlow
        if (templateFlow is InteractiveFlow) {
            return templateFlow
        }
        return authorizationFlowManager.defaultInteractiveFlow
    }

    /**
     * Return the [InteractiveFlow] identified by [id].
     * Return null if not found or if not of type [InteractiveFlow].
     */
    fun findByIdOrNull(id: String?): InteractiveFlow? {
        val authorizationFlow = id?.let(authorizationFlowManager::findByIdOrNull)
        return authorizationFlow as? InteractiveFlow
    }

    /**
     * Return the [InteractiveFlow] identified by [id].
     * Otherwise, throw an unrecoverable [BusinessException] with detailsId ```flow.web.invalid_flow```.
     */
    fun findById(id: String?): InteractiveFlow {
        return findByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "flow.web.invalid_flow",
            "flowId" to (id ?: "null")
        )
    }

    /**
     * Create a new [InteractiveFlowSession] for the end-user.
     *
     * This method is in charge of:
     * - validating the [uncheckedClientId]. Redirect the user to the error page of the default interactive auth flow in
     *   case of error.
     * - selecting the flow: Either the one provided by the [Client] or the default one.
     * - validating the [uncheckedScopes]. Redirect the user to the error page of the selected flow in case of error.
     * - validating the [uncheckedRedirectUri]. Redirect the user to the error page of the selected flow in case of
     *   error.
     * - validating the [uncheckedInvitationToken] if provided: checks that the invitation exists, is pending, and
     *   belongs to the same audience as the client. The invitation ID is stored on the session's OAuth2 record.
     * - creating the [InteractiveFlowSession].
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
        uncheckedCodeChallengeMethod: String? = null,
        uncheckedInvitationToken: String? = null
    ): Pair<InteractiveFlowSession, InteractiveFlow> {
        val (client, clientException) = try {
            val client = clientManager.parseRequestedClient(uncheckedClientId)
            if (!client.supportsGrantType(GrantType.AUTHORIZATION_CODE)) {
                throw businessExceptionOf(
                    detailsId = "authorize.unauthorized_grant_type",
                    descriptionId = "description.authorize.unauthorized_grant_type"
                )
            }
            client to null
        } catch (e: BusinessException) {
            null to e
        }

        val authorizationFlowId = client?.authorizationFlow?.id
        val defaultFlow = getDefaultInteractiveFlow()
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
                emptyList<EnabledScope>() to e
            }
        } else {
            emptyList<EnabledScope>() to null
        }

        val (redirectUri, redirectUriException) = if (client != null) {
            try {
                clientRedirectUriManager.parseRequestedRedirectUri(
                    client = client,
                    uncheckedRedirectUri = uncheckedRedirectUri,
                    recoverable = false
                ) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val (codeChallenge, codeChallengeMethod, pkceException) = parseCodeChallenge(
            uncheckedCodeChallenge = uncheckedCodeChallenge,
            uncheckedCodeChallengeMethod = uncheckedCodeChallengeMethod
        )

        val (invitation, invitationException) = if (!uncheckedInvitationToken.isNullOrBlank() && client != null) {
            try {
                invitationManager.validateToken(uncheckedInvitationToken, client.audience.id) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val session = oauth2Manager.startOAuth2Session(
            client = client,
            clientState = uncheckedClientState,
            clientNonce = uncheckedClientNonce,
            flow = flow,
            scopes = scopes,
            redirectUri = redirectUri,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            invitationId = invitation?.id,
            error = listOfNotNull(
                clientException,
                flowException,
                scopeException,
                redirectUriException,
                pkceException,
                invitationException
            ).firstOrNull()
        )
        return session to flow
    }

    /**
     * Parses and validates PKCE parameters (RFC 7636).
     *
     * - If `code_challenge` is present with no method, defaults to `S256`.
     * - If the method is provided but not recognized, returns an error.
     * - If `code_challenge` is missing, returns an error (PKCE is required for all clients per OAuth 2.1).
     */
    internal fun parseCodeChallenge(
        uncheckedCodeChallenge: String?,
        uncheckedCodeChallengeMethod: String?
    ): Triple<String?, CodeChallengeMethod?, BusinessException?> {
        if (uncheckedCodeChallenge.isNullOrBlank()) {
            return Triple(
                null, null, businessExceptionOf(
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
                    null, null, businessExceptionOf(
                        detailsId = "authorize.pkce.unsupported_method",
                        descriptionId = "description.authorize.pkce.unsupported_method",
                        values = arrayOf("method" to uncheckedCodeChallengeMethod)
                    )
                )
        }

        return Triple(uncheckedCodeChallenge, method, null)
    }

    /**
     * Return true if sign-up is allowed for the audience of the client that initiated the [session].
     *
     * Sign-up is allowed when the audience enables open registration, or when it enables invitation-based
     * registration and an invitation is bound to the session. Non-throwing counterpart of [checkSignUpAllowed].
     */
    suspend fun isSignUpAllowed(
        session: OnGoingInteractiveFlowSession
    ): Boolean {
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        val audience = clientManager.findClientById(oauth2.clientId).audience
        return isSignUpAllowed(audience, oauth2)
    }

    private fun isSignUpAllowed(audience: Audience, oauth2: InteractiveFlowSessionOAuth2): Boolean {
        return audience.signUpEnabled || (audience.invitationEnabled && oauth2.invitationId != null)
    }

    /**
     * Check that sign-up is allowed for the audience of the client that initiated the session's [oauth2]
     * request.
     *
     * Throws a [BusinessException] if:
     * - Both `signUpEnabled` and `invitationEnabled` are false on the audience.
     * - `signUpEnabled` is false and `invitationEnabled` is true but no invitation is bound to the request.
     *
     * Takes the already-fetched [oauth2] record (rather than the session) so a caller that also needs it —
     * e.g. for the invitation id — fetches it once.
     *
     * [recoverable] is the flag that exception carries, and it decides where the rejection goes. Password
     * sign-up passes `true`, because the person is in front of the page and can supply something else, so
     * the failure is rethrown to them; provider sign-up passes `false`, because that leg of the flow is
     * non-interactive and there is nothing for anyone to retry, so the session is failed instead.
     *
     * The exception is built with the constructor rather than a factory because the flag is a parameter
     * here, and no factory takes it as one.
     */
    suspend fun checkSignUpAllowed(
        oauth2: InteractiveFlowSessionOAuth2,
        recoverable: Boolean
    ) {
        // Resolve the audience once, reused for the check and the error below.
        val audience = clientManager.findClientById(oauth2.clientId).audience
        if (isSignUpAllowed(audience, oauth2)) {
            return
        }
        if (!audience.signUpEnabled && !audience.invitationEnabled) {
            throw BusinessException(
                recoverable = recoverable,
                detailsId = "flow.sign_up.disabled",
                descriptionId = "description.flow.sign_up.disabled"
            )
        }
        throw BusinessException(
            recoverable = recoverable,
            detailsId = "flow.sign_up.invitation_required",
            descriptionId = "description.flow.sign_up.invitation_required"
        )
    }
}

