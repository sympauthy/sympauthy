package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_WEB_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.view.DefaultAuthorizationFlowController.Companion.USER_FLOW_ENDPOINT
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of end-user authorization flows.
 * This does not handle client authentication (e.g. client_credentials).
 */
@Singleton
class AuthorizationFlowManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val scopeGrantingManager: UserScopeGrantingManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
) {

    /**
     * Note: The default web authentication flow is hardcoded since it is bundled with this authorization server.
     */
    val defaultWebAuthorizationFlow: WebAuthorizationFlow by lazy {
        val rootUri = uncheckedUrlsConfig.orThrow().root
            .let(UriBuilder::of)
            .path(USER_FLOW_ENDPOINT)
            .build()
        WebAuthorizationFlow(
            id = DEFAULT_WEB_AUTHORIZATION_FLOW_ID,
            signInUri = UriBuilder.of(rootUri).path("sign-in").build(),
            mfaUri = UriBuilder.of(rootUri).path("mfa").build(),
            mfaTotpChallengeUri = UriBuilder.of(rootUri).path("mfa/totp").build(),
            mfaTotpEnrollUri = UriBuilder.of(rootUri).path("mfa/totp/enroll").build(),
            collectClaimsUri = UriBuilder.of(rootUri).path("claims/edit").build(),
            validateClaimsUri = UriBuilder.of(rootUri).path("claims/validate").build(),
            errorUri = UriBuilder.of(rootUri).path("error").build(),
        )
    }

    /**
     * Return the [AuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): AuthorizationFlow? {
        if (id == DEFAULT_WEB_AUTHORIZATION_FLOW_ID) {
            return defaultWebAuthorizationFlow
        }
        return authorizationFlowsConfig.orThrow().flows
            .firstOrNull { it.id == id }
    }

    /**
     * Either return if the [authorizeAttempt] can be used to issue an access token to the [client]
     * or throws one of the following exceptions:
     * - [BusinessException] with "token.expired" if the attempt is not completed or has expired.
     * - [BusinessException] with "token.mismatching_client" if the attempt was initiated by a different client.
     */
    suspend fun checkCanIssueToken(
        authorizeAttempt: AuthorizeAttempt?,
        client: Client
    ): CompletedAuthorizeAttempt {
        if (authorizeAttempt !is CompletedAuthorizeAttempt) {
            throw businessExceptionOf("token.expired")
        }
        if (authorizeAttempt.expired) {
            throw businessExceptionOf("token.expired")
        }
        if (authorizeAttempt.clientId != client.id) {
            throw businessExceptionOf("token.mismatching_client")
        }
        return authorizeAttempt
    }

    /**
     * Complete the authorization flow for the given [authorizeAttempt] and return the completed [authorizeAttempt].
     *
     * If the allowAccessToClientWithoutScope flag is false and no scope has been granted, the [authorizeAttempt]
     * is marked as failed and the end-user is not allowed to continue to the client.
     *
     * On success, a [Consent] is persisted recording which scopes the user authorized for the client.
     * If an active consent already exists for this user+client pair, it is revoked and replaced.
     *
     * The list of [allCollectedClaims] may be provided to prevent loading them again.
     */
    suspend fun completeAuthorization(
        authorizeAttempt: AuthorizeAttempt,
        allCollectedClaims: List<CollectedClaim>,
    ): AuthorizeAttempt {
        val featuresConfig = uncheckedFeaturesConfig.orThrow()

        if (authorizeAttempt !is OnGoingAuthorizeAttempt) {
            return authorizeAttempt
        }
        var modifiedAuthorizedAttempt = authorizeAttempt
        // FIXME: Verify that the attempt is completable (has a user ?, more ?)

        // Grant only grantable scopes through the granting pipeline
        val grantScopesResult = scopeGrantingManager.grantScopes(
            authorizeAttempt = authorizeAttempt,
            allCollectedClaims = allCollectedClaims
        )
        modifiedAuthorizedAttempt = authorizeAttemptManager.setGrantedScopes(
            authorizeAttempt = modifiedAuthorizedAttempt,
            grantedScopes = grantScopesResult.grantedScopes,
            grantedBy = if (grantScopesResult.allAutoGranted) GrantedBy.AUTO else GrantedBy.RULE
        )

        val hasAnyScope = !modifiedAuthorizedAttempt.grantedScopes.isNullOrEmpty() ||
            !modifiedAuthorizedAttempt.consentedScopes.isNullOrEmpty()
        return if (!hasAnyScope && !featuresConfig.allowAccessToClientWithoutScope) {
            // Mark attempt as failed since no scope have been granted and the end-user is not allowed to continue
            // to the client in this state.
            authorizeAttemptManager.markAsFailedIfNotRecoverable(
                authorizeAttempt = authorizeAttempt,
                error = BusinessException(
                    recoverable = false,
                    detailsId = "flow.authorization_flow.complete.no_scope",
                    descriptionId = "description.flow.unauthorized_to_access_client",
                )
            )
        } else {
            val completedAttempt = authorizeAttemptManager.markAsComplete(modifiedAuthorizedAttempt)
            consentManager.saveGrantedConsent(
                userId = completedAttempt.userId,
                clientId = completedAttempt.clientId,
                scopes = completedAttempt.consentedScopes
            )
            completedAttempt
        }
    }
}
