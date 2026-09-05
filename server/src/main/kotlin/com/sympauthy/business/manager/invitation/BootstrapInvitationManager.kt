package com.sympauthy.business.manager.invitation

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.BootstrapInvitation
import com.sympauthy.config.model.BootstrapInvitationsConfig
import com.sympauthy.config.model.EnabledBootstrapInvitationsConfig
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
 * Nothing is created on a deployment whose configuration has errors. [ServiceReadyEvent] says only that
 * the embedded server started, so this asks [ConfigReadiness] for the verdict the readiness probe and the
 * startup banner both report, and skips the section when that verdict is negative. A token minted there
 * would be revoked by the startup that fixes the configuration, and its audience may not even exist.
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
    @Inject private val configReadiness: ConfigReadiness,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val consentManager: ConsentManager
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    /**
     * Whether this deployment configured no bootstrap invitation at all.
     *
     * An empty section, rather than one whose errors readiness is reporting: there is nothing to create,
     * and the skip below would be a line about a feature the operator never asked for.
     */
    private val noInvitationConfigured: Boolean
        get() = (bootstrapInvitationsConfig as? EnabledBootstrapInvitationsConfig)?.invitations?.isEmpty() == true

    override fun onApplicationEvent(event: ServiceReadyEvent) {
        if (noInvitationConfigured) return

        runBlocking {
            launch {
                if (configReadiness.getConfigurationErrors().isNotEmpty()) {
                    logger.warn(
                        "Bootstrap invitations: skipping — the configuration has errors and this server " +
                                "reports itself unready. No invitation was created and none was revoked. " +
                                "Fix the errors reported at startup and restart to obtain a token."
                    )
                    return@launch
                }
                bootstrapInvitationsConfig.orThrow().invitations.forEach { invitation ->
                    processBootstrapInvitation(invitation)
                }
            }
        }
    }

    @Suppress("MaxLineLength")
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

        // The integration tests read the token back out of the container log, through the testcontainers
        // library's getBootstrapInvitationToken. The format of the two lines below is its contract.
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
