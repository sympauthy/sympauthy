package com.sympauthy.business.manager.invitation

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.config.model.BootstrapInvitation
import com.sympauthy.config.model.BootstrapInvitationsConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.util.loggerForClass
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Creates bootstrap invitations defined in YAML configuration at application startup.
 *
 * For each configured bootstrap invitation:
 * - Checks if any user has already consented for the configured audience.
 * - If no consents exist: creates the invitation and logs the raw token (or the URL from `url-template`).
 * - If consents exist: skips — someone already registered, the bootstrap invitation is no longer needed.
 *
 * On each startup where the invitation is (re)created, previous bootstrap invitations for the same
 * audience are revoked and a new token is generated. The operator must use the token from the latest
 * startup log.
 */
@Singleton
class BootstrapInvitationManager(
    @Inject private val bootstrapInvitationsConfig: BootstrapInvitationsConfig,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val consentManager: ConsentManager
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        val invitations = bootstrapInvitationsConfig.orThrow().invitations
        if (invitations.isEmpty()) return

        runBlocking {
            launch {
                invitations.forEach { invitation ->
                    processBootstrapInvitation(invitation)
                }
            }
        }
    }

    private suspend fun processBootstrapInvitation(bootstrapInvitation: BootstrapInvitation) {
        val existingConsents = consentManager.findActiveConsentsByAudience(bootstrapInvitation.audienceId)
        if (existingConsents.isNotEmpty()) {
            logger.info(
                "Bootstrap invitation '${bootstrapInvitation.id}': skipping — " +
                        "${existingConsents.size} user(s) already consented for audience '${bootstrapInvitation.audienceId}'."
            )
            return
        }

        // Revoke previous bootstrap invitations for this audience
        revokePreviousBootstrapInvitations(bootstrapInvitation.audienceId)

        val (_, rawToken) = invitationManager.createInvitation(
            audienceId = bootstrapInvitation.audienceId,
            claims = bootstrapInvitation.claims,
            note = bootstrapInvitation.note ?: "Bootstrap invitation '${bootstrapInvitation.id}'",
            expiresAt = null,
            createdBy = InvitationCreatedBy.BOOTSTRAP,
            createdById = bootstrapInvitation.id
        )

        val urlTemplate = bootstrapInvitation.urlTemplate
        if (urlTemplate != null) {
            val url = urlTemplate.replace("{token}", rawToken)
            logger.info(
                "Bootstrap invitation '${bootstrapInvitation.id}' created for audience '${bootstrapInvitation.audienceId}'. " +
                        "Registration URL: $url"
            )
        } else {
            logger.info(
                "Bootstrap invitation '${bootstrapInvitation.id}' created for audience '${bootstrapInvitation.audienceId}'. " +
                        "Token: $rawToken"
            )
        }
    }

    private suspend fun revokePreviousBootstrapInvitations(audienceId: String) {
        val existingInvitations = invitationManager.findByAudienceId(audienceId)
            .filter { it.createdBy == InvitationCreatedBy.BOOTSTRAP && it.status == InvitationStatus.PENDING }
        for (invitation in existingInvitations) {
            invitationManager.revokeInvitation(invitation.id)
        }
    }
}
