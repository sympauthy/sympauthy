package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.auth.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.security.state
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
    @Inject private val claimValidationManager: WebAuthorizationFlowClaimValidationManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val scopeManager: ScopeManager
) {

    /**
     * Return the [WebAuthorizationFlow] of throw a non-recoverable [BusinessException] if not found.
     */
    fun findById(id: String?): WebAuthorizationFlow {
        val authorizationFlow = id?.let(authorizationFlowManager::findByIdOrNull)
        if (authorizationFlow !is WebAuthorizationFlow) {
            throw businessExceptionOf(
                detailsId = "flow.web.invalid_flow",
                values = arrayOf("flowId" to (id ?: ""))
            )
        }
        return authorizationFlow
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
        uncheckedRedirectUri: String?
    ): Pair<AuthorizeAttempt, WebAuthorizationFlow> {
        val (client, clientException) = try {
            clientManager.parseRequestedClient(uncheckedClientId) to null
        } catch (e: BusinessException) {
            null to e
        }

        val authorizationFlowId = client?.authorizationFlow?.id
        val (flow, flowException) = try {
            findById(authorizationFlowId) to null
        } catch (e: BusinessException) {
            findById(DEFAULT_AUTHORIZATION_FLOW_ID) to e
        }

        val (scopes, scopeException) = if (client != null) {
            try {
                scopeManager.parseRequestScope(uncheckedScopes) to null
            } catch (e: BusinessException) {
                emptyList<Scope>() to e
            }
        } else {
            emptyList<Scope>() to null
        }

        val (redirectUri, redirectUriException) = if (client != null) {
            try {
                parseRequestedRedirectUri(client, uncheckedRedirectUri) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val authorizeAttempt = authorizeAttemptManager.newAuthorizeAttempt(
            client = client,
            clientState = uncheckedClientState,
            authorizationFlow = flow,
            scopes = scopes,
            redirectUri = redirectUri,
            error = listOfNotNull(clientException, flowException, scopeException, redirectUriException).firstOrNull()
        )
        return authorizeAttempt to flow
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
            throw businessExceptionOf(
                detailsId = "flow.web.parse_requested_redirect_uri.missing"
            )
        }
        val redirectURI = try {
            URI(uncheckedRedirectUri)
        } catch (e: URISyntaxException) {
            throw businessExceptionOf(
                detailsId = "flow.web.parse_requested_redirect_uri.invalid",
                values = arrayOf("message" to (e.message ?: ""))
            )
        }

        if (client.allowedRedirectUris?.isNotEmpty() == true && !client.allowedRedirectUris.contains(redirectURI)) {
            throw businessExceptionOf(
                detailsId = "flow.web.parse_requested_redirect_uri.not_allowed"
            )
        }
        return redirectURI
    }

    /**
     * Retrieve the [AuthorizeAttempt] using the state.
     * Verify the following:
     * - the [AuthorizeAttempt] has not failed nor expired.
     * - the [AuthorizeAttempt] is associated with a [WebAuthorizationFlow].
     *
     * If all of those conditions are met, the [method] will be invoked.
     * The [AuthorizeAttempt] and [WebAuthorizationFlow] will be passed as parameters.
     *
     * Otherwise, an unrecoverable [BusinessException] will be thrown.
     *
     * If the [method] throws unrecoverable [BusinessException], it will be saved in the [AuthorizeAttempt]
     * using [AuthorizeAttemptManager.markAsFailedIfNotRecoverable] to prevent further usage.
     */
    suspend fun <T> extractFromStateVerifyThenRun(
        state: String?,
        method: suspend (AuthorizeAttempt, WebAuthorizationFlow) -> T
    ): T {
        val verifyResult = authorizeAttemptManager.verifyEncodedInternalState(state)
        val authorizeAttempt = when (verifyResult) {
            is SuccessVerifyEncodedStateResult -> verifyResult.authorizeAttempt
            is FailedVerifyEncodedStateResult -> {
                throw businessExceptionOf(
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId,
                )
            }
        }

        return try {
            val flow = this.findById(authorizeAttempt.authorizationFlowId)
            method(authorizeAttempt, flow)
        } catch (e: BusinessException) {
            if (authorizeAttempt is OnGoingAuthorizeAttempt) {
                authorizeAttemptManager.markAsFailedIfNotRecoverable(
                    authorizeAttempt = authorizeAttempt,
                    error = e,
                )
            }
            throw e
        }
    }

    /**
     * Retrieve the state from the [Authentication] then call [extractFromStateVerifyThenRun].
     */
    suspend fun <T> extractFromAuthenticationAndVerifyThenRun(
        authentication: Authentication,
        method: suspend (AuthorizeAttempt, WebAuthorizationFlow) -> T
    ): T = extractFromStateVerifyThenRun(
        state = authentication.state,
        method = method
    )

    /**
     * Same as [extractFromStateVerifyThenRun] except it also verifies that the [AuthorizeAttempt] is
     * an [OnGoingAuthorizeAttempt]. Otherwise, an unrecoverable [BusinessException] will be thrown and save into
     * the [AuthorizeAttempt] using [AuthorizeAttemptManager.markAsFailedIfNotRecoverable].
     */
    suspend fun <T> extractOnGoingFromStateAndRun(
        state: String?,
        method: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow) -> Unit
    ) = extractFromStateVerifyThenRun(state) { authorizeAttempt, flow ->
        if (authorizeAttempt is OnGoingAuthorizeAttempt) {
            method(authorizeAttempt, flow)
        } else {
            throw businessExceptionOf(
                detailsId = "flow.web.invalid_status"
            )
        }
    }

    /**
     * Retrieve the state from the [Authentication] then call [extractFromStateVerifyThenRun].
     * If the [AuthorizeAttempt] is not an [OnGoingAuthorizeAttempt], an unrecoverable [BusinessException] will be
     * thrown.
     */
    suspend fun <T> extractOnGoingFromAuthenticationAndVerifyThenRun(
        authentication: Authentication,
        method: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow) -> T
    ): T = extractFromStateVerifyThenRun(state = authentication.state) { authorizeAttempt, flow ->
        if (authorizeAttempt is OnGoingAuthorizeAttempt) {
            method(authorizeAttempt, flow)
        } else {
            throw businessExceptionOf(
                detailsId = "flow.web.invalid_status"
            )
        }
    }

    /**
     * Retrieves the following then call the [method]:
     * - the [OnGoingAuthorizeAttempt] associated to the state in the [Authentication].
     * - the [User] associated to the [OnGoingAuthorizeAttempt].
     * - the [WebAuthorizationFlow] associated to the [OnGoingAuthorizeAttempt].
     *
     * Throws an unrecoverable [BusinessException] if either:
     * - the [OnGoingAuthorizeAttempt] is not using a [WebAuthorizationFlow].
     * - there is no [User] associated to the [OnGoingAuthorizeAttempt]
     *
     * Note: This method uses the [extractOnGoingFromAuthenticationAndVerifyThenRun] internally to
     * retrieve the [OnGoingAuthorizeAttempt] from [Authentication].
     */
    suspend fun <T> extractOnGoingWithUserFromAuthenticationAndVerifyThenRun(
        authentication: Authentication,
        method: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow, User) -> T
    ): T = extractOnGoingFromAuthenticationAndVerifyThenRun(authentication) { authorizeAttempt, flow ->
        val user = authorizeAttemptManager.getUser(authorizeAttempt)
        method(authorizeAttempt, flow, user)
    }

    /**
     * Retrieve the user from the [OnGoingAuthorizeAttempt] or [CompletedAuthorizeAttempt] associated to the state
     * in the [Authentication].
     * This method uses the [extractFromStateVerifyThenRun] internally to retrieve the [AuthorizeAttempt].
     */
    suspend fun <T> extractUserFromAuthenticationAndVerifyThenRun(
        authentication: Authentication,
        method: suspend (AuthorizeAttempt, WebAuthorizationFlow, User?) -> T
    ): T = extractFromStateVerifyThenRun(state = authentication.state) { authorizeAttempt, flow ->
        val user = authorizeAttemptManager.getUserOrNull(authorizeAttempt)
        method(authorizeAttempt, flow, user)
    }


    /**
     * Retrieves the current status of the provided [authorizeAttempt] and completes the authorization flow
     * if it is determined to be complete.
     *
     * The list of [collectedClaims] may be provided to prevent loading
     *
     * This method internally calls [getStatus] to evaluate the state of the authorization flow. If the
     * returned status indicates that the flow is complete, it proceeds to invoke
     * [AuthorizationFlowManager.completeAuthorization] to finalize the process.
     * The resulting status is then returned.
     */
    suspend fun getStatusAndCompleteIfNecessary(
        authorizeAttempt: AuthorizeAttempt,
        collectedClaims: List<CollectedClaim>? = null
    ): WebAuthorizationFlowStatus {
        val loadedCollectedClaims =
            collectedClaims ?: collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt)
        val status = getStatus(
            authorizeAttempt = authorizeAttempt,
            collectedClaims = loadedCollectedClaims
        )
        if (status.complete) {
            authorizationFlowManager.completeAuthorization(
                authorizeAttempt = authorizeAttempt,
                collectedClaims = loadedCollectedClaims
            )
        }
        return status
    }

    /**
     * Return the status of the [authorizeAttempt] if the end-user is going through a web authorization flow.
     */
    internal fun getStatus(
        authorizeAttempt: AuthorizeAttempt,
        collectedClaims: List<CollectedClaim>
    ): WebAuthorizationFlowStatus {
        return when (authorizeAttempt) {
            is FailedAuthorizeAttempt -> getStatusForFailedAuthorizeAttempt()
            is CompletedAuthorizeAttempt -> getStatusForCompletedAuthorizeAttempt()
            is OnGoingAuthorizeAttempt -> getStatusForOnGoingAuthorizeAttempt(authorizeAttempt, collectedClaims)
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
    internal fun getStatusForOnGoingAuthorizeAttempt(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        collectedClaims: List<CollectedClaim>
    ): WebAuthorizationFlowStatus {
        val missingUser = authorizeAttempt.userId == null
        val missingRequiredClaims = !collectedClaimManager.areAllRequiredClaimCollected(collectedClaims)
        val missingMediaForClaimValidation = claimValidationManager.getReasonsToSendValidationCode(collectedClaims)
            .map(ValidationCodeReason::media)
            .distinct()

        return WebAuthorizationFlowStatus(
            missingUser = missingUser,
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
}

