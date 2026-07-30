package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.auth.OAuth2AuthorizeInteractiveFlowStatus
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for the OAuth2 / OpenID Connect authorization flow initiated at
 * `/api/oauth2/authorize`.
 *
 * Owns the step state machine (which page the end-user must go through next, expressed as an abstract
 * [InteractiveFlowStep]) and the completion effect (grant the requested scopes, record the consent, and mark the
 * session complete — or fail it when no scope can be granted and the client may not be accessed without
 * one). Producing the concrete redirect URL from a [InteractiveFlowStep] is left to the API boundary.
 */
@Singleton
class OAuth2AuthorizeInteractiveFlowPurposeHandler(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val claimValidationManager: InteractiveAuthFlowSessionClaimValidationManager,
    @Inject private val scopeGrantingManager: UserScopeGrantingManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
    @Inject private val clientManagerProvider: Provider<ClientManager>,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE

    override suspend fun getNextStep(session: OnGoingInteractiveFlowSession): InteractiveFlowPurposeStepResult {
        // Fetch the OAuth2 record once and thread it into the status computation and the sign-in/up branch.
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        val status = computeStatus(session, oauth2)
        return when {
            status.missingUser -> InteractiveFlowPurposeStepResult.Pending(
                session,
                if (oauth2.invitationId != null) InteractiveFlowStep.SignUp else InteractiveFlowStep.SignIn
            )

            status.missingRequiredClaims ->
                InteractiveFlowPurposeStepResult.Pending(session, InteractiveFlowStep.CollectClaims)

            status.missingMediaForClaimValidation.isNotEmpty() -> InteractiveFlowPurposeStepResult.Pending(
                session,
                InteractiveFlowStep.ValidateClaims(status.missingMediaForClaimValidation.first())
            )

            // Every step this purpose requires is satisfied; append the MFA purpose (if any) so it runs as
            // the final gate before the authorization code is issued, then resolve.
            else -> resolveWithMfa(session)
        }
    }

    /**
     * Resolve this purpose, first appending the MFA purpose the [session] requires — unless one is already
     * present — so MFA is the last thing the end-user goes through before the flow completes.
     */
    private suspend fun resolveWithMfa(session: OnGoingInteractiveFlowSession): InteractiveFlowPurposeStepResult {
        val hasMfaPurpose = session.purposes.any {
            it == InteractiveFlowPurpose.MFA_ENROLLMENT || it == InteractiveFlowPurpose.MFA_CHALLENGE
        }
        if (!hasMfaPurpose) {
            val mfaPurpose = requiredMfaPurpose(session)
            if (mfaPurpose != null) {
                return InteractiveFlowPurposeStepResult.Resolved(sessionManager.appendPurpose(session, mfaPurpose))
            }
        }
        return InteractiveFlowPurposeStepResult.Resolved(session)
    }

    /**
     * Return the MFA purpose this OAuth2 session must go through before it can complete, or null when no MFA
     * step is required. Evaluated only when MFA is enabled (TOTP configured):
     * - the user is already enrolled -> [InteractiveFlowPurpose.MFA_CHALLENGE] (takes precedence over required)
     * - the user signed up during this session -> [InteractiveFlowPurpose.MFA_ENROLLMENT] (skippable iff MFA
     *   is not required)
     * - MFA is required and the user is not enrolled -> [InteractiveFlowPurpose.MFA_ENROLLMENT] (forced)
     * - otherwise (sign-in, not enrolled, not required) -> null
     */
    internal suspend fun requiredMfaPurpose(session: OnGoingInteractiveFlowSession): InteractiveFlowPurpose? {
        val mfaConfig = uncheckedMfaConfig.orThrow()
        if (!mfaConfig.enabled) return null
        val userId = session.userId ?: return null
        val enrolled = totpManager.findConfirmedEnrollments(userId).isNotEmpty()
        return when {
            enrolled -> InteractiveFlowPurpose.MFA_CHALLENGE
            session.signedUp -> InteractiveFlowPurpose.MFA_ENROLLMENT
            mfaConfig.required -> InteractiveFlowPurpose.MFA_ENROLLMENT
            else -> null
        }
    }

    /**
     * Compute the flow progress of the ongoing [session] from the collected claims and consent.
     *
     * Takes the already-fetched [oauth2] record (rather than re-fetching it) so the caller loads it once.
     * MFA is not part of this status: it is a separate purpose appended once the user's own steps are done.
     */
    internal suspend fun computeStatus(
        session: OnGoingInteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2
    ): OAuth2AuthorizeInteractiveFlowStatus {
        val consentedScopes = oauth2.consentedScopes ?: emptyList()
        val identifierClaims = session.userId?.let {
            collectedClaimManager.findIdentifierByUserId(it)
        } ?: emptyList()
        val consentedClaims = session.userId?.let {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(it, consentedScopes)
        } ?: emptyList()
        val missingUser = session.userId == null
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

        return OAuth2AuthorizeInteractiveFlowStatus(
            missingUser = missingUser,
            missingRequiredClaims = missingRequiredClaims,
            missingMediaForClaimValidation = missingMediaForClaimValidation
        )
    }

    /**
     * Complete the authorization flow for the ongoing [session] and return the completed session.
     *
     * If the allowAccessToClientWithoutScope flag is false and no scope has been granted, the session is
     * marked as failed and the end-user is not allowed to continue to the client.
     *
     * On success, a [com.sympauthy.business.model.oauth2.Consent] is persisted recording which scopes the
     * user authorized for the client. If an active consent already exists for this user+client pair, it is
     * revoked and replaced.
     */
    override suspend fun complete(session: OnGoingInteractiveFlowSession): InteractiveFlowSession {
        val featuresConfig = uncheckedFeaturesConfig.orThrow()

        // Fetch all collected claims regardless of consent so the granting manager can access them all.
        val userId = session.userId
            ?: throw internalBusinessExceptionOf("flow.authorization_flow.complete.missing_user")
        val allClaims = collectedClaimManager.findByUserId(userId)

        // Grant only grantable scopes through the granting pipeline
        val grantScopesResult = scopeGrantingManager.grantScopes(
            session = session,
            allClaims = allClaims
        )
        val oauth2 = oauth2Manager.setGrantedScopes(
            session = session,
            grantedScopes = grantScopesResult.grantedScopes,
            grantedBy = grantScopesResult.grantedBy
        )

        val hasAnyScope = !oauth2.grantedScopes.isNullOrEmpty() ||
                !oauth2.consentedScopes.isNullOrEmpty()
        return if (!hasAnyScope && !featuresConfig.allowAccessToClientWithoutScope) {
            // Mark the session as failed since no scope have been granted and the end-user is not allowed to
            // continue to the client in this state.
            sessionManager.markAsFailedIfNotRecoverable(
                session = session,
                error = BusinessException(
                    recoverable = false,
                    detailsId = "flow.authorization_flow.complete.no_scope",
                    descriptionId = "description.flow.unauthorized_to_access_client",
                )
            )
        } else {
            val completedSession = sessionManager.makePurposeAsComplete(session, purpose)
            val client = clientManagerProvider.get().findClientById(oauth2.clientId)
            consentManager.saveConsent(
                userId = userId,
                audienceId = client.audience.id,
                clientId = oauth2.clientId,
                scopes = oauth2.consentedScopes ?: emptyList()
            )
            completedSession
        }
    }
}
