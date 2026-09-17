package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.auth.OAuth2AuthorizeInteractiveFlowStatus
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.flow.TerminalEffectResult
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for the OAuth2 / OpenID Connect authorization flow initiated at
 * `/api/oauth2/authorize`.
 *
 * Owns the step logic (which page the end-user must go through next, expressed as an abstract
 * [InteractiveFlowStep]), the MFA purpose it requires before the flow can complete, and the completion effect
 * (grant the requested scopes and record the consent — or fail when no scope can be granted and the client may
 * not be accessed without one). Producing the concrete redirect URL from a [InteractiveFlowStep] is left to the
 * API boundary; every session mutation is left to the engine.
 */
@Singleton
class OAuth2AuthorizeInteractiveFlowPurposeHandler(
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager,
    @Inject private val claimValidationManager: InteractiveAuthFlowSessionClaimValidationManager,
    @Inject private val scopeGrantingManager: UserScopeGrantingManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val totpManager: TotpManager,
    @Inject private val uncheckedFeaturesConfig: FeaturesConfig,
) : InteractiveFlowPurposeHandler {

    companion object {
        /** What a value an operator may know the existence of, but never the content of, is published as. */
        private const val PRESENT = "present"
        private const val ABSENT = "absent"
    }

    override val purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        // Fetch the OAuth2 record once and thread it into the status computation and the sign-in/up branch.
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        val status = computeStatus(session, oauth2)
        return when {
            status.missingUser ->
                if (oauth2.invitationId != null) InteractiveFlowStep.SignUp else InteractiveFlowStep.SignIn

            status.missingRequiredClaims -> InteractiveFlowStep.CollectClaims

            status.missingMediaForClaimValidation.isNotEmpty() ->
                InteractiveFlowStep.ValidateClaims(status.missingMediaForClaimValidation.first())

            else -> null
        }
    }

    /**
     * The client's request as it stands, the consent and grant decisions taken on it, and whether each of the
     * three values that must not be published is there.
     *
     * `state` and `nonce` are reported as present or absent because they exist so that only the client and the
     * browser hold them. `code_challenge` is neither — it travelled in the authorize request and the verifier
     * is never stored here — and is reduced to its method because a 43-character string tells an operator
     * nothing they can act on. The authorization code is keyed by session id in a table this never reads.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val oauth2 = readOAuth2OrNull(session)
        return listOf(
            PurposeDebugInformation("Client", oauth2?.clientId),
            PurposeDebugInformation("Redirect URI", oauth2?.redirectUri),
            PurposeDebugInformation("Requested scopes", oauth2?.requestedScopes?.joinToString(" ")),
            PurposeDebugInformation("Consented scopes", oauth2?.consentedScopes?.joinToString(" ")),
            PurposeDebugInformation("Consented at", oauth2?.consentedAt?.toString()),
            PurposeDebugInformation("Consented by", oauth2?.consentedBy?.wireName),
            PurposeDebugInformation("Granted scopes", oauth2?.grantedScopes?.joinToString(" ")),
            PurposeDebugInformation("Granted at", oauth2?.grantedAt?.toString()),
            PurposeDebugInformation("Granted by", oauth2?.grantedBy?.wireName),
            PurposeDebugInformation("Invitation", oauth2?.invitationId?.toString()),
            PurposeDebugInformation("State", oauth2?.let { presence(it.state != null) }),
            PurposeDebugInformation("Nonce", oauth2?.let { presence(it.nonce != null) }),
            PurposeDebugInformation("Code challenge", oauth2?.let(::codeChallengePresence))
        )
    }

    /**
     * The OAuth2 record of [session], or null where there is none and where the one there is cannot be read
     * back.
     *
     * Two shapes reach here that the flow itself never asks about, and both answer null rather than throwing.
     * A session another purpose started has no such record, and the fetch refuses to go looking for one. A
     * request that failed validation has a record whose client and redirect URI were never known — the row is
     * written anyway, so the session can carry the error that explains itself — and
     * [InteractiveFlowSessionOAuth2] admits neither as null, so reading it back is an internal failure. That
     * session is the one an operator opens this endpoint for, so it answers with the labels unset; what went
     * wrong is on the session's own error keys, which is where they would look for it.
     */
    private suspend fun readOAuth2OrNull(session: InteractiveFlowSession): InteractiveFlowSessionOAuth2? {
        if (session.initiatingPurpose != InteractiveFlowPurpose.OAUTH2_AUTHORIZE) return null
        return try {
            oauth2Manager.fetchOAuth2OrNull(session)
        } catch (_: BusinessException) {
            null
        }
    }

    /**
     * Whether a PKCE challenge came with the request and, where one did, the method it was built with.
     */
    private fun codeChallengePresence(oauth2: InteractiveFlowSessionOAuth2): String {
        if (oauth2.codeChallenge == null) return ABSENT
        return oauth2.codeChallengeMethod?.let { "$PRESENT (${it.value})" } ?: PRESENT
    }

    private fun presence(present: Boolean) = if (present) PRESENT else ABSENT

    /**
     * The MFA purpose this OAuth2 session must go through before it can complete, so MFA runs as the final gate
     * before the authorization code is issued. Empty once an MFA purpose is already present.
     */
    override suspend fun followUpPurposes(session: OnGoingInteractiveFlowSession): List<InteractiveFlowPurpose> {
        val hasMfaPurpose = session.purposes.any {
            it == InteractiveFlowPurpose.MFA_ENROLLMENT || it == InteractiveFlowPurpose.MFA_CHALLENGE
        }
        if (hasMfaPurpose) return emptyList()
        return listOfNotNull(requiredMfaPurpose(session))
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
        val enrolled = totpManager.isEnrolled(userId)
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
     * MFA is not part of this status: it is a separate purpose the OAuth2 purpose requires once its own steps
     * are done.
     *
     * Everything about the claims is the audience's, resolved from the client [oauth2] names: what is required,
     * what is read back as collected, and what is left needing a validation code. A claim restricted to another
     * audience neither holds up a flow that did not start from it nor is asked to be confirmed by one.
     */
    internal suspend fun computeStatus(
        session: OnGoingInteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2
    ): OAuth2AuthorizeInteractiveFlowStatus {
        // Nothing below this is answerable without a user, and a flow missing one is sent to sign in whatever
        // the rest would have said — so the claims are neither read nor the audience resolved for it.
        val userId = session.userId ?: return OAuth2AuthorizeInteractiveFlowStatus(missingUser = true)

        val consentedScopes = oauth2.consentedScopes ?: emptyList()
        val audienceId = oauth2Manager.getAudienceId(oauth2)
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(userId)
        val consentedClaims = consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
            userId = userId,
            audienceId = audienceId,
            consentedScopes = consentedScopes
        )
        val allClaims = (identifierClaims + consentedClaims).distinctBy { it.claim.id }
        val missingRequiredClaims = !consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
            allClaims, audienceId, consentedScopes
        )
        val missingMediaForClaimValidation = claimValidationManager.getReasonsToSendValidationCode(
            audienceId = audienceId,
            identifierClaims = identifierClaims,
            consentedClaims = consentedClaims
        )
            .map(ValidationCodeReason::media)
            .distinct()

        return OAuth2AuthorizeInteractiveFlowStatus(
            missingUser = false,
            missingRequiredClaims = missingRequiredClaims,
            missingMediaForClaimValidation = missingMediaForClaimValidation
        )
    }

    /**
     * Grant the requested scopes for the ongoing [session] and record the consent.
     *
     * If the allowAccessToClientWithoutScope flag is false and no scope has been granted, returns
     * [TerminalEffectResult.Fail] so the engine fails the session and the end-user is not allowed to continue
     * to the client.
     *
     * On success, a [com.sympauthy.business.model.oauth2.Consent] is persisted recording which scopes the user
     * authorized for the client. If an active consent already exists for this user+client pair, it is revoked
     * and replaced.
     *
     * The invitation, if one brought the end-user here, is consumed here too rather than at sign-up. An
     * invitation is spent on an account that comes to exist, so a flow that is abandoned leaves it pending
     * and the invitee's link still works. It is also what settles two sign-ups holding one invitation: this
     * runs inside the completion transaction, so the first flow to reach it takes it and the second is
     * refused. See [com.sympauthy.data.model.SessionScoped].
     *
     * The claims handed to the granting pipeline are the ones this authorization's audience has, read that way
     * rather than read whole and narrowed after: a rule deciding on a claim restricted to another audience
     * publishes it, the granted scope being the decision leaving the server and the authorization webhook
     * posting the value itself. Consent is not applied to them — what a rule may branch on and what the
     * end-user agreed to disclose to the client are different questions.
     */
    override suspend fun applyTerminalEffect(session: OnGoingInteractiveFlowSession): TerminalEffectResult {
        val featuresConfig = uncheckedFeaturesConfig.orThrow()

        val userId = session.userId
            ?: throw internalBusinessExceptionOf("flow.authorization_flow.complete.missing_user")
        // Fetched once here and threaded downward: the granting pipeline and the consent below both answer for
        // the audience it names, and neither reads the record again.
        val oauth2BeforeGranting = oauth2Manager.fetchOAuth2(session)
        val audienceId = oauth2Manager.getAudienceId(oauth2BeforeGranting)
        val audienceClaims = collectedClaimManager.findByUserIdAndAudience(userId, audienceId)

        // Grant only grantable scopes through the granting pipeline
        val grantScopesResult = scopeGrantingManager.grantScopes(
            session = session,
            oauth2 = oauth2BeforeGranting,
            audienceClaims = audienceClaims
        )
        val oauth2 = oauth2Manager.setGrantedScopes(
            session = session,
            grantedScopes = grantScopesResult.grantedScopes,
            grantedBy = grantScopesResult.grantedBy
        )

        val hasAnyScope = !oauth2.grantedScopes.isNullOrEmpty() ||
                !oauth2.consentedScopes.isNullOrEmpty()
        if (!hasAnyScope && !featuresConfig.allowAccessToClientWithoutScope) {
            // No scope has been granted and the end-user is not allowed to continue to the client in this state.
            return TerminalEffectResult.Fail(
                businessExceptionOf(
                    detailsId = "flow.authorization_flow.complete.no_scope",
                    descriptionId = "description.flow.unauthorized_to_access_client",
                )
            )
        }

        consentManager.saveConsent(
            userId = userId,
            audienceId = audienceId,
            clientId = oauth2.clientId,
            scopes = oauth2.consentedScopes ?: emptyList()
        )
        oauth2.invitationId?.let { invitationManager.consumeInvitation(it, userId) }
        return TerminalEffectResult.Proceed
    }
}
