package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.flow.ProviderUserEstablisher
import com.sympauthy.business.manager.flow.ProviderUserEstablishment
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.ClaimValueValidator
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
import kotlin.jvm.optionals.getOrNull

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
    @Inject private val claimValueValidator: ClaimValueValidator,
    @Inject private val lockManager: LockManager,
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
     *
     * The whole of it runs under the lock over the identity being linked, and re-reads that identity inside
     * it: the caller resolved it as unknown before this transaction began, and one of the other two writers
     * of a committed link may have taken it since. See [LockKey.ProviderSubject].
     */
    @Transactional
    open suspend fun createOrAssociateUserWithProviderUserInfo(
        sessionId: UUID,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val authConfig = uncheckedAuthConfig.orThrow()
        val identifierClaims = resolveIdentifierClaims(authConfig, provider, providerUserInfo)
        return lockManager.withLock(LockKey.ProviderSubject(provider.id, providerUserInfo.subject)) {
            checkSubjectStillFree(provider, providerUserInfo.subject)
            if (authConfig.userMergingEnabled) {
                createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(
                    sessionId, identifierClaims, provider, providerUserInfo
                )
            } else {
                createUserWithProviderUserInfo(sessionId, identifierClaims, provider, providerUserInfo)
            }
        }
    }

    /**
     * Throw `user.create_with_provider.subject_taken` when a committed account has been linked to the
     * [subject] of [provider] since the callback read it as unknown.
     *
     * **Recoverable on purpose.** The end-user going through the provider again resolves the link that now
     * exists and signs them in, which is the outcome they came for — where failing the flow would leave them
     * with an account they cannot reach. It covers the account this would create as well as the one it would
     * merge into: a provisional link written now against a subject already committed elsewhere fails at the
     * promotion instead, at the end of the flow and with nothing left to retry.
     */
    private suspend fun checkSubjectStillFree(provider: EnabledProvider, subject: String) {
        if (providerClaimsManager.findByProviderAndSubject(provider, subject) != null) {
            throw recoverableBusinessExceptionOf(
                detailsId = "user.create_with_provider.subject_taken",
                descriptionId = "description.user.create_with_provider.subject_taken",
                "providerId" to provider.id
            )
        }
    }

    /**
     * Associate the provider to the account the identifier claims it asserts resolve to, or create one
     * where they resolve to none.
     *
     * [UserManager.findByIdentifierClaims] is the right read for resolving: matching every asserted value
     * is what makes the answer one account rather than a choice between two, and merging into the wrong
     * one attaches a stranger's provider to somebody's account. It is not the read that says whether the
     * values are free, which is why creating goes through [createUserWithIdentifierClaims] below.
     *
     * The identifier claim values are collected and copied as first-party data. We want this information
     * to be stable and not be affected by changes from the third party in the future.
     * Otherwise, an update from a provider may break our uniqueness and cause uncontrolled side effects.
     */
    @Transactional
    internal open suspend fun createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(
        sessionId: UUID,
        identifierClaims: Map<String, Pair<Claim, Any>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)

        val user = existingUser ?: createUserWithIdentifierClaims(sessionId, identifierClaims)

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
     * Create a new [User] with the provider user info. An account this server already holds is never
     * reached from here, so a provider asserting an identifier one of them owns is refused rather than
     * merged: the end-user has an account and signs in with it.
     *
     * There is no resolving read above this. Whether an account matches every asserted value is a
     * question only merging asks; the one this branch asks is whether the values are free, which
     * [createUserWithIdentifierClaims] answers and which refuses strictly more.
     */
    @Transactional
    internal open suspend fun createUserWithProviderUserInfo(
        sessionId: UUID,
        identifierClaims: Map<String, Pair<Claim, Any>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val user = createUserWithIdentifierClaims(sessionId, identifierClaims)
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
     * Create the provisional account the provider's [identifierClaims] are written to, having checked that
     * a committed account does not already hold one of those values.
     *
     * Throws `user.create_with_provider.existing_user`, naming the claim that lost, when one does.
     * [UserManager.findTakenIdentifierOrNull] is the rule: a value is free across every configured
     * identifier claim or not at all, so asking instead for an account matching *every* value the provider
     * asserts lets one holding a single value through — and the account created over it dies at its own
     * promotion, unrecoverably, at the end of a flow the end-user can no longer act on. Refusing here says
     * the true thing at the moment it is still worth hearing.
     *
     * The account named is none: it does not exist yet, and the values it is about to claim are nobody's.
     *
     * **It takes no lock, deliberately.** What it creates is provisional, and the uniqueness of a
     * provisional account's identifier is settled when it is promoted — under the key that promotion takes
     * over these same values. Locking here would serialise sign-ups against each other, which is the thing
     * the provisional row exists to avoid, and would put a second lock in a transaction already holding one
     * over the provider subject. See `docs/provisional-user.md`.
     */
    private suspend fun createUserWithIdentifierClaims(
        sessionId: UUID,
        identifierClaims: Map<String, Pair<Claim, Any>>
    ): User {
        val updates = identifierClaims.values.map { (claim, value) ->
            CollectedClaimUpdate(claim = claim, value = Optional.of(value))
        }
        val taken = userManager.findTakenIdentifierOrNull(
            userId = null,
            claimIds = uncheckedAuthConfig.orThrow().identifierClaims,
            valuesByClaimId = collectedClaimManager.getIdentifierComparisonValuesIn(updates)
        )
        if (taken != null) {
            throw businessExceptionOf(
                "user.create_with_provider.existing_user",
                "claim" to taken.claimId,
                "userId" to "${taken.userId}"
            )
        }
        val user = userManager.createUser(sessionId)
        collectedClaimManager.update(user, updates)
        return user
    }

    /**
     * The value [providerUserInfo] asserts for each of [EnabledAuthConfig.identifierClaims], resolved
     * against the claim configuring it and cleaned by [ClaimValueValidator] the way a value collected from
     * an end-user is.
     *
     * An asserted value goes through the validator rather than onto the account as it arrived, because
     * these are the values the account is then identified by and a comparison on one of them is exact: an
     * address a provider spells in mixed case, or pads, is otherwise an identity none of this deployment's
     * own spellings ever reaches. The refusal a value that is not of its claim's type earns is the
     * validator's own, recoverable, so the end-user is returned to the step and may sign in another way.
     *
     * Throws `user.create_with_provider.missing_identifier_claim_config` where a configured identifier
     * claim names no claim this deployment declares, and `user.create_with_provider.missing_identifier_claim`
     * where the provider asserts nothing for one — which a value cleaning away to nothing at all is.
     */
    private suspend fun resolveIdentifierClaims(
        authConfig: EnabledAuthConfig,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): Map<String, Pair<Claim, Any>> {
        return authConfig.identifierClaims.associateWith { claimId ->
            val claim = claimManager.findByIdOrNull(claimId)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim_config",
                    "claim" to claimId
                )
            val value = providerUserInfo.getClaimValueOrNull(claimId)
                ?.let { claimValueValidator.validateAndCleanValueForClaim(claim, it).getOrNull() }
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim",
                    "providerId" to provider.id,
                    "claim" to claimId
                )
            claim to value
        }
    }

}
