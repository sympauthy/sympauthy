package com.sympauthy.business.manager.invitation

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties
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
    @Inject private val bootstrapInvitationProperties: List<BootstrapInvitationConfigurationProperties>,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val consentManager: ConsentManager
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        if (bootstrapInvitationProperties.isEmpty()) return

        runBlocking {
            launch {
                bootstrapInvitationProperties.forEach { properties ->
                    processBootstrapInvitation(properties)
                }
            }
        }
    }

    private suspend fun processBootstrapInvitation(properties: BootstrapInvitationConfigurationProperties) {
        val audienceId = properties.audience
        if (audienceId.isNullOrBlank()) {
            logger.warn("Bootstrap invitation '${properties.id}' is missing the 'audience' property. Skipping.")
            return
        }

        val existingConsents = consentManager.findActiveConsentsByAudience(audienceId)
        if (existingConsents.isNotEmpty()) {
            logger.info(
                "Bootstrap invitation '${properties.id}': skipping — " +
                        "${existingConsents.size} user(s) already consented for audience '$audienceId'."
            )
            return
        }

        // Revoke previous bootstrap invitations for this audience
        revokePreviousBootstrapInvitations(audienceId)

        val (invitation, rawToken) = invitationManager.createInvitation(
            audienceId = audienceId,
            claims = properties.claims,
            note = properties.note ?: "Bootstrap invitation '${properties.id}'",
            expiresAt = null,
            createdBy = InvitationCreatedBy.BOOTSTRAP,
            createdById = properties.id
        )

        val urlTemplate = properties.urlTemplate
        if (urlTemplate != null) {
            val url = urlTemplate.replace("{token}", rawToken)
            logger.info(
                "Bootstrap invitation '${properties.id}' created for audience '$audienceId'. " +
                        "Registration URL: $url"
            )
        } else {
            logger.info(
                "Bootstrap invitation '${properties.id}' created for audience '$audienceId'. " +
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
