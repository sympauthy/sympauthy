package com.sympauthy.business.manager.invitation

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.config.ConfigReadiness
import com.sympauthy.config.model.BootstrapInvitation
import com.sympauthy.config.model.BootstrapInvitationsConfig
import com.sympauthy.config.model.DisabledBootstrapInvitationsConfig
import com.sympauthy.config.model.EnabledBootstrapInvitationsConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class BootstrapInvitationManagerTest {

    @MockK
    lateinit var configReadiness: ConfigReadiness

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var consentManager: ConsentManager

    private val bootstrapInvitation = BootstrapInvitation(
        id = "first-admin",
        audienceId = "default",
        urlTemplate = null,
        claims = null,
        note = null
    )

    /** The subject is built per test: the configuration it reads is what each case varies. */
    private fun manager(config: BootstrapInvitationsConfig) = BootstrapInvitationManager(
        bootstrapInvitationsConfig = config,
        configReadiness = configReadiness,
        invitationManager = invitationManager,
        consentManager = consentManager
    )

    private fun invitation(
        createdBy: InvitationCreatedBy,
        status: InvitationStatus = InvitationStatus.PENDING
    ) = Invitation(
        id = UUID.randomUUID(),
        audienceId = "default",
        tokenPrefix = "abcd1234",
        claims = null,
        note = null,
        status = status,
        createdBy = createdBy,
        createdById = null,
        consumedByUserId = null,
        createdAt = LocalDateTime.now(),
        expiresAt = LocalDateTime.now().plusDays(7),
        consumedAt = null,
        revokedAt = null,
    )

    private fun expectNoInvitationCreated() {
        coVerify(exactly = 0) {
            invitationManager.createInvitation(any(), any(), any(), any(), any(), any(), any())
            invitationManager.revokeInvitation(any())
        }
    }

    @Test
    fun `onApplicationEvent - Creates the invitation when the configuration has no error`() {
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { consentManager.findActiveConsentsByAudience("default") } returns emptyList()
        coEvery { invitationManager.findByAudienceId("default") } returns emptyList()
        coEvery {
            invitationManager.createInvitation(any(), any(), any(), any(), any(), any(), any())
        } returns (mockk<Invitation>() to "rawToken")

        manager(EnabledBootstrapInvitationsConfig(listOf(bootstrapInvitation))).onApplicationEvent(mockk())

        coVerify {
            invitationManager.createInvitation(
                audienceId = "default",
                claims = null,
                note = "Bootstrap invitation 'first-admin'",
                expiresAt = null,
                createdBy = InvitationCreatedBy.BOOTSTRAP,
                createdById = "first-admin",
                clientScopeIds = any()
            )
        }
    }

    @Test
    fun `onApplicationEvent - Creates nothing when the configuration has errors`() {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Invalid provider"))

        manager(EnabledBootstrapInvitationsConfig(listOf(bootstrapInvitation))).onApplicationEvent(mockk())

        coVerify(exactly = 0) { consentManager.findActiveConsentsByAudience(any()) }
        expectNoInvitationCreated()
    }

    @Test
    fun `onApplicationEvent - Creates nothing when the bootstrap section itself is invalid`() {
        coEvery { configReadiness.getConfigurationErrors() } returns listOf(Exception("Unknown audience"))

        assertDoesNotThrow {
            manager(DisabledBootstrapInvitationsConfig(emptyList())).onApplicationEvent(mockk())
        }
        expectNoInvitationCreated()
    }

    @Test
    fun `onApplicationEvent - Reads no configuration when no bootstrap invitation is configured`() {
        manager(EnabledBootstrapInvitationsConfig(emptyList())).onApplicationEvent(mockk())

        coVerify(exactly = 0) { configReadiness.getConfigurationErrors() }
        expectNoInvitationCreated()
    }

    @Test
    fun `onApplicationEvent - Creates nothing when a user already consented for the audience`() {
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { consentManager.findActiveConsentsByAudience("default") } returns listOf(mockk())

        manager(EnabledBootstrapInvitationsConfig(listOf(bootstrapInvitation))).onApplicationEvent(mockk())

        expectNoInvitationCreated()
    }

    @Test
    fun `onApplicationEvent - Revokes the previous bootstrap invitation before creating the new one`() {
        val previous = invitation(createdBy = InvitationCreatedBy.BOOTSTRAP)
        val consumed = invitation(createdBy = InvitationCreatedBy.BOOTSTRAP, status = InvitationStatus.CONSUMED)
        val fromAdmin = invitation(createdBy = InvitationCreatedBy.ADMIN)
        coEvery { configReadiness.getConfigurationErrors() } returns emptyList()
        coEvery { consentManager.findActiveConsentsByAudience("default") } returns emptyList()
        coEvery { invitationManager.findByAudienceId("default") } returns listOf(previous, consumed, fromAdmin)
        coEvery { invitationManager.revokeInvitation(previous.id) } returns mockk()
        coEvery {
            invitationManager.createInvitation(any(), any(), any(), any(), any(), any(), any())
        } returns (mockk<Invitation>() to "rawToken")

        manager(EnabledBootstrapInvitationsConfig(listOf(bootstrapInvitation))).onApplicationEvent(mockk())

        coVerifyOrder {
            invitationManager.revokeInvitation(previous.id)
            invitationManager.createInvitation(any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            invitationManager.revokeInvitation(consumed.id)
            invitationManager.revokeInvitation(fromAdmin.id)
        }
    }
}
