package com.sympauthy.business.manager.flow.link

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for [InteractiveFlowPurpose.LINK_PROVIDER].
 *
 * Drives the end-user to authorize with the target provider (its id is on the session's link-provider record)
 * so it can be linked to the session's already-fixed [OnGoingInteractiveFlowSession.userId]. The purpose is
 * resolved once the durable link exists — i.e. the target provider is linked to the session user.
 *
 * The link itself is created in the provider callback
 * ([com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2ProviderManager.signInOrSignUpUsingProvider]),
 * next to claim resolution and behind the conflict checks — mirroring how the re-authentication gate confirms
 * in the callback (the resolved provider claims exist only there). This handler stays pure: it only reads the
 * durable link state, and has no terminal effect.
 */
@Singleton
class LinkProviderInteractiveFlowPurposeHandler(
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.LINK_PROVIDER

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        val providerId = linkProviderManager.fetchLinkProviderOrNull(session)?.providerId ?: return null
        val userId = session.userId ?: return null
        val alreadyLinked = providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, providerId) != null
        return if (alreadyLinked) null else InteractiveFlowStep.AuthorizeProvider(providerId)
    }
}
