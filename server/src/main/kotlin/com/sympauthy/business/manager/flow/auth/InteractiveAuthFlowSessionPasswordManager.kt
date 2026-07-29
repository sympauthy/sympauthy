package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findAnyClaimMatching
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Manager in charge of the authentication and registration of an end-user going through an interactive auth flow
 * using a password.
 *
 * This manager uses [CollectedClaimManager] (not consent-aware) because identifier claims
 * may not be part of the scopes requested by the client.
 */
@Singleton
open class InteractiveAuthFlowSessionPasswordManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Manager: InteractiveFlowSessionOAuth2Manager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val userManager: UserManager,
    @Inject private val userRepository: UserRepository,
    @Inject private val claimValueMapper: ClaimValueMapper,
    @Inject private val userMapper: UserMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * True if the sign-in by login/password is enabled. False otherwise.
     */
    val signInEnabled: Boolean
        get() = uncheckedAuthConfig.orThrow().let {
            it.byPassword.enabled && it.identifierClaims.isNotEmpty()
        }

    /**
     * True if the sign-up by login/password is enabled. False otherwise.
     */
    val signUpEnabled: Boolean
        get() = uncheckedAuthConfig.orThrow().let {
            it.byPassword.enabled && it.identifierClaims.isNotEmpty()
        }

    /**
     * Find the end-user with a claim matching the [login].
     * The claims used to match the login are configured in [EnabledAuthConfig.identifierClaims].
     */
    internal suspend fun findByLogin(login: String): User? {
        val identifierClaims = uncheckedAuthConfig.orThrow().identifierClaims
        val userInfo = collectedClaimRepository.findAnyClaimMatching(
            claimIds = identifierClaims,
            value = claimValueMapper.toEntity(login) ?: return null,
        )
        return userInfo?.userId
            ?.let { userRepository.findById(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Sign in the end-user using a [login] and a [password] and associate the [InteractiveFlowSession] with the [User]
     * associated to the [login]. Finally, return the updated session.
     */
    suspend fun signInWithPassword(
        session: OnGoingInteractiveFlowSession,
        login: String?,
        password: String?
    ): InteractiveFlowSession {
        if (!signInEnabled) {
            throw internalBusinessExceptionOf("flow.password.sign_in.disabled")
        }
        if (login.isNullOrBlank() || password.isNullOrBlank()) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_in.invalid",
                descriptionId = "description.flow.password.sign_in.invalid"
            )
        }

        val user = findByLogin(login)
        // The user does not exist or has been created using a third-party provider.
        if (user == null || user.status != UserStatus.ENABLED) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_in.invalid",
                descriptionId = "description.flow.password.sign_in.invalid"
            )
        }

        if (!passwordManager.arePasswordMatching(user, password)) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_in.invalid",
                descriptionId = "description.flow.password.sign_in.invalid"
            )
        }

        // Update the session with the id of the user so they can retrieve their access token.
        val updatedSession = sessionManager.setAuthenticatedUserId(session, user.id)

        // Call complete on the authorization flow in case there is no more step to complete.
        val status = interactiveAuthFlowSessionManager.getStatus(updatedSession)
        return interactiveAuthFlowSessionManager.completeIfNecessary(
            session = updatedSession,
            status = status
        )
    }

    /**
     * Create a new user with the provided claims([unfilteredUpdates]) and [password].
     */
    @Transactional
    open suspend fun signUpWithClaimsAndPassword(
        session: OnGoingInteractiveFlowSession,
        unfilteredUpdates: List<CollectedClaimUpdate>,
        password: String
    ): InteractiveFlowSession {
        val oauth2 = oauth2Manager.fetchOAuth2(session)
        interactiveAuthFlowSessionManager.checkSignUpAllowed(oauth2, recoverable = true)

        val claimUpdateMap = claimManager.listIdentifierClaims().associateWith { claim ->
            unfilteredUpdates.firstOrNull { it.claim == claim }
        }
        val claimUpdates = claimUpdateMap.values.filterNotNull()

        checkForMissingClaims(claimUpdateMap)
        passwordManager.validatePassword(password)
        checkForConflictingUsers(claimUpdates)

        val user = userManager.createUser()
        collectedClaimManager.update(
            user = user,
            updates = claimUpdates
        )
        passwordManager.createPassword(user, password)

        // Apply invitation claims and consume the invitation
        invitationManager.applyInvitationClaimsAndConsume(oauth2.invitationId, user.id)

        // Update the session with the id of the user so they can retrieve their access token.
        val updatedSession = sessionManager.setAuthenticatedUserId(session, user.id)

        // Call complete on the authorization flow in case there is no more step to complete.
        val status = interactiveAuthFlowSessionManager.getStatus(updatedSession)
        return interactiveAuthFlowSessionManager.completeIfNecessary(
            session = updatedSession,
            status = status,
        )
    }

    /**
     * Throws a recoverable business exception with detailsId ```flow.password.sign_up.missing_claim```
     * if any of the claims in [signUpClaimUpdateMap] is missing a value.
     */
    internal fun checkForMissingClaims(signUpClaimUpdateMap: Map<Claim, CollectedClaimUpdate?>) {
        val missingClaim = signUpClaimUpdateMap.filterValues { it == null }
            .keys
            .firstOrNull()
        if (missingClaim != null) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_up.missing_claim",
                descriptionId = "description.flow.password.sign_up.missing_claim",
                "claim" to missingClaim.id
            )
        }
    }

    /**
     * Throws a recoverable business exception with detailsId ```flow.password.sign_up.existing```
     * if any of the [claims] conflict with another user login.
     *
     * As a user can use any of the provided [claims] to login, we must ensure that the values are unique
     * to a user and across the claims.
     */
    internal suspend fun checkForConflictingUsers(claims: List<CollectedClaimUpdate>) {
        val claimIds = claims.map { it.claim.id }
        val values = claims
            .mapNotNull { it.value?.getOrNull() }
            .mapNotNull(claimValueMapper::toEntity)
        val existingCollectedClaims = collectedClaimRepository.findAnyClaimMatching(claimIds, values)
        if (existingCollectedClaims.isNotEmpty()) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_up.existing",
                descriptionId = "description.flow.password.sign_up.existing"
            )
        }
    }
}
