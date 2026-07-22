package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.reauth.ReAuthenticationManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.reauth.ProviderAttachContext
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.data.repository.PasswordRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Consumer of the generic re-authentication component that implements the interactive **provider attach** flow.
 *
 * When a provider sign-in collides with an existing account (unknown subject, but the identifier claims match),
 * [startAttach] stores the incoming provider identity as a provisional row and opens a re-authentication challenge.
 * Once the end-user proves ownership of the target account (via the reused sign-in flow), [completeAttach] promotes
 * the provisional identity into a permanent link. If the end-user declines, [decline] discards it and — where
 * sign-up is allowed — creates a separate account instead.
 */
@Singleton
open class WebAuthorizationFlowProviderAttachManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val passwordRepository: PasswordRepository,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerConfigManager: ProviderManager,
    @Inject private val reAuthenticationManager: ReAuthenticationManager,
    @Inject private val userManager: UserManager,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager
) {

    private val logger = LoggerFactory.getLogger(WebAuthorizationFlowProviderAttachManager::class.java)

    /**
     * Build the context the sign-in page needs to render the re-authentication banner for the attach in progress on
     * [authorizeAttempt]. Throws a recoverable exception if the re-authentication has expired.
     */
    suspend fun getAttachContext(
        authorizeAttempt: OnGoingAuthorizeAttempt
    ): ProviderAttachContext {
        val reAuthenticationId = authorizeAttempt.reauthenticationAttemptId
            ?: throw internalBusinessExceptionOf("flow.reauth.provider_attach.missing_provisional")
        val reAuthentication = reAuthenticationManager
            .getPendingOrNull(reAuthenticationId, ReAuthenticationPurpose.PROVIDER_ATTACH)
            ?: throw recoverableBusinessExceptionOf(
                detailsId = "flow.reauth.expired",
                descriptionId = "description.flow.reauth.expired"
            )
        val provisional = providerClaimsManager.findProvisionalByAttempt(authorizeAttempt.id)
            ?: throw internalBusinessExceptionOf("flow.reauth.provider_attach.missing_provisional")
        val provider = providerConfigManager.findByIdAndCheckEnabled(provisional.providerId)

        val targetUserId = reAuthentication.targetUserId
        val availableMethods = buildSet {
            if (passwordRepository.findByUserId(targetUserId).isNotEmpty()) add(ReAuthenticationMethod.PASSWORD)
            if (providerClaimsManager.findByUserId(targetUserId).isNotEmpty()) add(ReAuthenticationMethod.PROVIDER)
        }
        return ProviderAttachContext(
            targetIdentifierClaims = collectedClaimManager.findIdentifierByUserId(targetUserId),
            providerId = provider.id,
            providerName = provider.name,
            availableMethods = availableMethods
        )
    }

    /**
     * Start an interactive attach: persist the incoming provider identity as provisional (scoped to the attempt),
     * open a re-authentication challenge for [targetUser], and reference it from the attempt. Does not authenticate
     * the end-user.
     */
    @Transactional
    open suspend fun startAttach(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        targetUser: User,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): OnGoingAuthorizeAttempt {
        providerClaimsManager.saveProvisionalUserInfo(
            provider = provider,
            targetUserId = targetUser.id,
            rawProviderClaims = providerUserInfo,
            authorizeAttemptId = authorizeAttempt.id
        )
        val reAuthentication = reAuthenticationManager.start(
            targetUserId = targetUser.id,
            purpose = ReAuthenticationPurpose.PROVIDER_ATTACH
        )
        logger.info(
            "Provider attach requested: provider={} subject={} targetUserId={} clientId={} attemptId={}",
            provider.id, providerUserInfo.subject, targetUser.id, authorizeAttempt.clientId, authorizeAttempt.id
        )
        return authorizeAttemptManager.setReAuthenticationAttempt(authorizeAttempt, reAuthentication.id)
    }

    /**
     * Promote the provisional provider identity into a permanent link on the re-authenticated target account, then
     * authenticate the end-user as that account and clear the re-authentication reference.
     */
    @Transactional
    open suspend fun completeAttach(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        reAuthentication: PassedReAuthenticationAttempt
    ): OnGoingAuthorizeAttempt {
        providerClaimsManager.confirmProvisionalUserInfo(authorizeAttempt.id)
        val withUser = authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, reAuthentication.targetUserId)
        val cleared = authorizeAttemptManager.clearReAuthentication(withUser)
        logger.info(
            "Provider attach completed: targetUserId={} method={} clientId={} attemptId={}",
            reAuthentication.targetUserId, reAuthentication.passedMethod, authorizeAttempt.clientId, authorizeAttempt.id
        )
        return cleared
    }

    /**
     * Decline the attach: discard the provisional identity and, where sign-up is allowed for the client's audience,
     * create a **separate** account linked to the incoming provider identity (provider-only, so the colliding
     * identifier claims are not copied as first-party login credentials). Otherwise reject the sign-in.
     */
    @Transactional
    open suspend fun decline(
        authorizeAttempt: OnGoingAuthorizeAttempt
    ): AuthorizeAttempt {
        val provisional = providerClaimsManager.findProvisionalByAttempt(authorizeAttempt.id)
            ?: throw internalBusinessExceptionOf("flow.reauth.provider_attach.missing_provisional")

        // Rejects with a recoverable exception if the audience does not allow sign-up.
        webAuthorizationFlowManager.checkSignUpAllowed(authorizeAttempt, recoverable = true)

        val provider = providerConfigManager.findByIdAndCheckEnabled(provisional.providerId)
        providerClaimsManager.deleteProvisionalByAttempt(authorizeAttempt.id)

        val newUser = userManager.createUser()
        providerClaimsManager.saveUserInfo(
            provider = provider,
            userId = newUser.id,
            rawProviderClaims = provisional.userInfo
        )

        val withUser = authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, newUser.id)
        val cleared = authorizeAttemptManager.clearReAuthentication(withUser)
        logger.info(
            "Provider attach declined: created separate account userId={} provider={} subject={} clientId={} attemptId={}",
            newUser.id, provider.id, provisional.userInfo.subject, authorizeAttempt.clientId, authorizeAttempt.id
        )
        return cleared
    }
}
