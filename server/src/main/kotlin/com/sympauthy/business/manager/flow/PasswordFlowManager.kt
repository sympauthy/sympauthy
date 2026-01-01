package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizeManager
import com.sympauthy.business.manager.password.PasswordManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.config.model.EnabledPasswordAuthConfig
import com.sympauthy.config.model.PasswordAuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.findAnyClaimMatching
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Manager in charge of the authentication and registration of end-user using a password.
 */
@Singleton
open class PasswordFlowManager(
    @Inject private val authorizeManager: AuthorizeManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val passwordManager: PasswordManager,
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val userManager: UserManager,
    @Inject private val userRepository: UserRepository,
    @Inject private val claimValueMapper: ClaimValueMapper,
    @Inject private val userMapper: UserMapper,
    @Inject private val uncheckedPasswordAuthConfig: PasswordAuthConfig
) {

    /**
     * True if the sign-in by login/password is enabled. False otherwise.
     */
    val signInEnabled: Boolean
        get() = uncheckedPasswordAuthConfig.orThrow().enabled

    /**
     * True if the sign-up by login/password is enabled. False otherwise.
     */
    val signUpEnabled: Boolean
        get() = uncheckedPasswordAuthConfig.orThrow().enabled

    /**
     * Find the end-user with a claim matching the [login].
     * The claims used to match the login are configured in [EnabledPasswordAuthConfig.loginClaims].
     */
    internal suspend fun findByLogin(login: String): User? {
        val loginClaims = uncheckedPasswordAuthConfig.orThrow().loginClaims
        val userInfo = collectedClaimRepository.findAnyClaimMatching(
            claimIds = loginClaims.map(OpenIdClaim::id),
            value = claimValueMapper.toEntity(login) ?: return null,
        )
        return userInfo?.userId
            ?.let { userRepository.findById(it) }
            ?.let(userMapper::toUser)
    }

    /**
     * Sign-in the end-user using a [login] and its [password].
     */
    suspend fun signInWithPassword(
        authorizeAttempt: AuthorizeAttempt,
        login: String?,
        password: String?
    ): AuthorizationFlowResult {
        if (!signInEnabled) {
            throw recoverableBusinessExceptionOf("flow.password.sign_in.disabled")
        }
        if (login.isNullOrBlank() || password.isNullOrBlank()) {
            throw recoverableBusinessExceptionOf("flow.password.sign_in.invalid")
        }

        val user = findByLogin(login)
        // The user does not exist or has been created using a third-party provider.
        if (user == null || user.status != UserStatus.ENABLED) {
            throw recoverableBusinessExceptionOf("flow.password.sign_in.invalid")
        }

        if (!passwordManager.arePasswordMatching(user, password)) {
            throw recoverableBusinessExceptionOf("flow.password.sign_in.invalid")
        }

        // Update the authorize attempt with the id of the user so they can retrieve their access token.
        authorizeManager.setAuthenticatedUserId(authorizeAttempt, user.id)

        // Check if sign-up is completed
        val claims = collectedClaimManager.findReadableUserInfoByUserId(userId = user.id)
        return authorizationFlowManager.checkIfAuthorizationIsComplete(
            user = user,
            collectedClaims = claims
        )
    }

    /**
     * Return a list of [Claim] the end-user can use as a login for the password flow.
     */
    fun getSignInClaims(): List<Claim> {
        return uncheckedPasswordAuthConfig.orThrow()
            .loginClaims
            .mapNotNull { claimManager.findById(it.id) }
    }

    /**
     * Return a list of [Claim] that may be collected alongside the end-user password during the sign-up.
     */
    fun getSignUpClaims(): List<Claim> {
        return getSignInClaims()
    }

    /**
     * Create a new user with the provided claims([unfilteredUpdates]) and [password].
     */
    @Transactional
    open suspend fun signUpWithClaimsAndPassword(
        authorizeAttempt: AuthorizeAttempt,
        unfilteredUpdates: List<CollectedClaimUpdate>,
        password: String
    ): AuthorizationFlowResult {
        val claimUpdateMap = getSignUpClaims().associateWith { claim ->
            unfilteredUpdates.firstOrNull { it.claim == claim }
        }
        val claimUpdates = claimUpdateMap.values.filterNotNull()

        checkForMissingClaims(claimUpdateMap)
        passwordManager.validatePassword(password)
        checkForConflictingUsers(claimUpdates)

        val user = userManager.createUser()
        val collectedClaims = collectedClaimManager.update(
            user = user,
            updates = claimUpdates
        )
        passwordManager.createPassword(user, password)

        // Update the authorize attempt with the id of the user so they can retrieve their access token.
        authorizeManager.setAuthenticatedUserId(authorizeAttempt, user.id)

        return authorizationFlowManager.checkIfAuthorizationIsComplete(
            user = user,
            collectedClaims = collectedClaims
        )
    }

    internal fun checkForMissingClaims(claimUpdateMap: Map<Claim, CollectedClaimUpdate?>) {
        val missingClaim = claimUpdateMap.filterValues { it == null }
            .keys
            .firstOrNull()
        if (missingClaim != null) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.password.sign_up.missing_claim",
                descriptionId = "flow.password.sign_up.missing_claim",
                "claim" to missingClaim.id
            )
        }
    }

    /**
     * Throws a sign_up.existing error if any of the [claims] conflict with another user login.
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
            throw recoverableBusinessExceptionOf("flow.password.sign_up.existing")
        }
    }
}
