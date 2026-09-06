package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.ProviderUserEstablisher
import com.sympauthy.business.manager.flow.ProviderUserEstablishment
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.CreateOrAssociateResult
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * OAuth2-authorize consumer implementation of [ProviderUserEstablisher].
 *
 * Establishes the end-user from a provider round-trip during a sign-in / sign-up
 * ([com.sympauthy.business.model.flow.InteractiveFlowPurpose.OAUTH2_AUTHORIZE]): it enforces the sign-up
 * rules, creates or associates the [User] (honoring `auth.user-merging-enabled` and the configured
 * identifier claims), and applies the claims any invitation carries. The generic
 * [com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2ProviderManager] owns the provider
 * protocol and delegates this outcome here.
 */
@Singleton
open class InteractiveAuthFlowSessionProviderEstablisher(
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val userManager: UserManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
) : ProviderUserEstablisher {

    override suspend fun establishNewProviderUser(
        session: OnGoingInteractiveFlowSession,
        provider: EnabledProvider,
        rawUserInfo: RawProviderClaims
    ): ProviderUserEstablishment {
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        interactiveAuthFlowSessionManager.checkSignUpAllowed(oauth2, recoverable = false)
        val result = createOrAssociateUserWithProviderUserInfo(session.id, provider, rawUserInfo)
        invitationManager.applyInvitationClaims(oauth2.invitationId, result.user)
        // `created` is false when the provider was merged into an existing account (sign-in, not sign-up).
        return ProviderUserEstablishment(userId = result.user.id, signedUp = result.created)
    }

    /**
     * Create a new [User] or associate the provider to an existing [User]. Then update the provider user
     * info with the newly collected [providerUserInfo].
     *
     * Depending on ```auth.user-merging-enabled```, we may instead associate the [providerUserInfo] to
     * an existing user based on the configured identifier claims.
     *
     * An account this creates is provisional for [sessionId] until the session completes; one it associates
     * to was already committed and stays so, which is why the rows written below take their session id from
     * the user rather than from [sessionId]. See [com.sympauthy.data.model.SessionScoped].
     */
    @Transactional
    open suspend fun createOrAssociateUserWithProviderUserInfo(
        sessionId: UUID,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val authConfig = uncheckedAuthConfig.orThrow()
        val identifierClaims = resolveIdentifierClaims(authConfig, provider, providerUserInfo)
        return if (authConfig.userMergingEnabled) {
            createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(
                sessionId, identifierClaims, provider, providerUserInfo
            )
        } else {
            createUserWithProviderUserInfo(sessionId, identifierClaims, provider, providerUserInfo)
        }
    }

    /**
     * Create a new [User] or associate it to an existing user that has matching values for all configured
     * identifier claims.
     *
     * The identifier claim values are collected and copied as first-party data. We want this information
     * to be stable and not be affected by changes from the third party in the future.
     * Otherwise, an update from a provider may break our uniqueness and cause uncontrolled side effects.
     */
    @Transactional
    internal open suspend fun createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(
        sessionId: UUID,
        identifierClaims: Map<String, Pair<Claim, String>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)

        val user = existingUser ?: userManager.createUser(sessionId).also { newUser ->
            saveIdentifierClaims(newUser, identifierClaims)
        }

        providerClaimsManager.saveUserInfo(
            provider = provider,
            userId = user.id,
            sessionId = user.sessionId,
            rawProviderClaims = providerUserInfo
        )
        return CreateOrAssociateResult(
            created = existingUser == null,
            user = user
        )
    }

    /**
     * Create a new [User] with the provider user info.
     * Without user merging, if a user already exists with the same identifier claims, throw an error
     * as the user must sign in with their existing account.
     */
    @Transactional
    internal open suspend fun createUserWithProviderUserInfo(
        sessionId: UUID,
        identifierClaims: Map<String, Pair<Claim, String>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)
        if (existingUser != null) {
            throw businessExceptionOf("user.create_with_provider.existing_user")
        }

        val user = userManager.createUser(sessionId)
        saveIdentifierClaims(user, identifierClaims)
        providerClaimsManager.saveUserInfo(
            provider = provider,
            userId = user.id,
            sessionId = user.sessionId,
            rawProviderClaims = providerUserInfo
        )
        return CreateOrAssociateResult(
            created = true,
            user = user
        )
    }

    /**
     * Extract identifier claim values from [providerUserInfo] and resolve the corresponding
     * [Claim] business objects.
     */
    private suspend fun resolveIdentifierClaims(
        authConfig: EnabledAuthConfig,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): Map<String, Pair<Claim, String>> {
        return authConfig.identifierClaims.associateWith { claimId ->
            val value = providerUserInfo.getClaimValueOrNull(claimId)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim",
                    "providerId" to provider.id,
                    "claim" to claimId
                )
            val claim = claimManager.findByIdOrNull(claimId)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim_config",
                    "claim" to claimId
                )
            claim to value
        }
    }

    private suspend fun saveIdentifierClaims(
        user: User,
        identifierClaims: Map<String, Pair<Claim, String>>
    ) {
        collectedClaimManager.update(
            user = user,
            updates = identifierClaims.map { (_, claimAndValue) ->
                CollectedClaimUpdate(
                    claim = claimAndValue.first,
                    value = Optional.of(claimAndValue.second)
                )
            }
        )
    }
}
