package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.client.authorization.webhook.AuthorizationWebhookClient
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookRequest
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookResult
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

/**
 * Scope granting method that delegates authorization decisions to an external HTTP server
 * via a synchronous webhook call.
 *
 * When a client has an [AuthorizationWebhook][com.sympauthy.business.model.client.AuthorizationWebhook] configured,
 * this manager calls the webhook endpoint with the user context and requested scopes,
 * and maps the response to grant or deny decisions.
 *
 * This method is inserted before the rule-based granting in the
 * [UserScopeGrantingManager] pipeline.
 *
 * @see [UserScopeGrantingManager.getScopeGrantingMethods]
 */
@Singleton
class AuthorizationWebhookUserScopeGrantingManager(
    @Inject private val clientManagerProvider: Provider<ClientManager>,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val authorizationWebhookClient: AuthorizationWebhookClient
) {

    private val logger = loggerForClass()

    /**
     * Call the authorization webhook configured on the client to determine which scopes to grant or deny.
     *
     * If no webhook is configured on the client, returns an empty result so scopes flow to the next method.
     *
     * The webhook may grant additional grantable scopes beyond those requested, as long as they are within
     * the client's `allowed-scopes` configuration.
     */
    suspend fun applyAuthorizationWebhookScopeGranting(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
        collectedClaims: List<CollectedClaim>
    ): ScopeGrantingMethodResult {
        val onGoingAttempt = authorizeAttempt as OnGoingAuthorizeAttempt
        val client = clientManagerProvider.get().findClientById(onGoingAttempt.clientId)
        val authorizationWebhook = client.authorizationWebhook
            ?: return ScopeGrantingMethodResult()

        val request = AuthorizationWebhookRequest(
            userId = onGoingAttempt.userId.toString(),
            clientId = onGoingAttempt.clientId,
            requestedScopes = requestedScopes.map { it.scope },
            claims = collectedClaims.associate { it.claim.id to it.value }
        )

        return when (val result = authorizationWebhookClient.callWebhook(authorizationWebhook, request)) {
            is AuthorizationWebhookResult.Success -> {
                val response = result.response
                val granted = mutableListOf<Scope>()
                val declined = mutableListOf<Scope>()

                // Process requested scopes
                requestedScopes.forEach { scope ->
                    when (response.scopes[scope.scope]) {
                        GRANT -> granted.add(scope)
                        else -> declined.add(scope)
                    }
                }

                // Process additional scopes granted by the webhook beyond the requested ones
                val requestedScopeIds = requestedScopes.map { it.scope }.toSet()
                val allowedScopeIds = client.allowedScopes?.map { it.scope }?.toSet()
                response.scopes
                    .filter { (scopeId, decision) -> decision == GRANT && scopeId !in requestedScopeIds }
                    .forEach { (scopeId, _) ->
                        val scope = scopeManager.find(scopeId)
                        if (scope is GrantableUserScope && (allowedScopeIds == null || scopeId in allowedScopeIds)) {
                            granted.add(scope)
                        }
                    }

                logger.debug(
                    "Authorization webhook for client {} granted {} scope(s) and declined {} scope(s).",
                    client.id, granted.size, declined.size
                )

                ScopeGrantingMethodResult(
                    source = GrantedBy.WEBHOOK,
                    grantedScopes = granted,
                    declinedScopes = declined
                )
            }

            is AuthorizationWebhookResult.Failure -> {
                logger.warn(
                    "Authorization webhook call to {} for client {} failed: {}",
                    authorizationWebhook.url, client.id, result.message
                )
                when (authorizationWebhook.onFailure) {
                    AuthorizationWebhookOnFailure.DENY_ALL -> ScopeGrantingMethodResult(
                        source = GrantedBy.WEBHOOK,
                        grantedScopes = emptyList(),
                        declinedScopes = requestedScopes
                    )

                    AuthorizationWebhookOnFailure.FALLBACK_TO_RULES -> ScopeGrantingMethodResult(
                        source = GrantedBy.WEBHOOK
                    )
                }
            }
        }
    }

    companion object {
        private const val GRANT = "grant"
    }
}
