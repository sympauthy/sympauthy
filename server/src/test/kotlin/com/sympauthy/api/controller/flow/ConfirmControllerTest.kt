package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.ConfirmFlowResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.security.StateAuthentication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class ConfirmControllerTest {

    @MockK
    lateinit var confirmManager: InteractiveFlowSessionConfirmManager

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    private val controller by lazy {
        ConfirmController(confirmManager, interactiveAuthFlowSessionControllerUtil)
    }

    private val session = mockk<OnGoingInteractiveFlowSession>()
    private val flow = mockk<InteractiveFlow>()

    @Test
    fun `getConfirmation - Returns the action context (no re-auth ahead) while the confirmation is pending`() =
        runTest {
            val confirm = mockk<InteractiveFlowSessionConfirm> {
                every { confirmed } returns false
                every { action } returns ConfirmActionType.ENROLL_MFA
                every { clientId } returns "client-x"
            }
            every { session.purposes } returns
                listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT)
            coEvery { confirmManager.fetchConfirmOrNull(session) } returns confirm
            coEvery {
                interactiveAuthFlowSessionControllerUtil
                    .fetchOnGoingSessionThenRunAndRedirect<ConfirmFlowResource, ConfirmFlowResource>(
                        eq("encoded-state"), any(), any(), any()
                    )
            } coAnswers {
                val run = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> ConfirmFlowResource?>(1)
                val mapResultToResource = arg<suspend (ConfirmFlowResource) -> ConfirmFlowResource>(3)
                mapResultToResource(run(session, flow)!!)
            }

            val result = controller.getConfirmation(StateAuthentication("encoded-state"))

            assertEquals("ENROLL_MFA", result.action)
            assertEquals("client-x", result.initiatingClientId)
            // No REAUTHENTICATION purpose in the chain -> the UI is told no re-auth follows.
            assertFalse(result.requiresReauthentication)
            assertNull(result.redirectUrl)
        }

    @Test
    fun `getConfirmation - Flags requires_reauthentication when a re-auth step is still ahead`() = runTest {
        val confirm = mockk<InteractiveFlowSessionConfirm> {
            every { confirmed } returns false
            every { action } returns ConfirmActionType.LINK_PROVIDER
            every { clientId } returns null
        }
        every { session.purposes } returns listOf(
            InteractiveFlowPurpose.CONFIRM,
            InteractiveFlowPurpose.REAUTHENTICATION,
            InteractiveFlowPurpose.LINK_PROVIDER,
        )
        every { session.completedPurposes } returns emptyList()
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns confirm
        coEvery {
            interactiveAuthFlowSessionControllerUtil
                .fetchOnGoingSessionThenRunAndRedirect<ConfirmFlowResource, ConfirmFlowResource>(
                    eq("encoded-state"), any(), any(), any()
                )
        } coAnswers {
            val run = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> ConfirmFlowResource?>(1)
            val mapResultToResource = arg<suspend (ConfirmFlowResource) -> ConfirmFlowResource>(3)
            mapResultToResource(run(session, flow)!!)
        }

        val result = controller.getConfirmation(StateAuthentication("encoded-state"))

        assertEquals("LINK_PROVIDER", result.action)
        assertNull(result.initiatingClientId)
        // An unresolved REAUTHENTICATION purpose is ahead -> warn the UI up front.
        assertTrue(result.requiresReauthentication)
    }

    @Test
    fun `getConfirmation - Redirects once the confirmation was already given`() = runTest {
        val confirm = mockk<InteractiveFlowSessionConfirm> { every { confirmed } returns true }
        val redirectUri = URI.create("https://auth.example.com/flow/mfa/enrollment?state=encoded-state")
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns confirm
        coEvery {
            interactiveAuthFlowSessionControllerUtil
                .fetchOnGoingSessionThenRunAndRedirect<ConfirmFlowResource, ConfirmFlowResource>(
                    eq("encoded-state"), any(), any(), any()
                )
        } coAnswers {
            val run = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> ConfirmFlowResource?>(1)
            val mapRedirectUriToResource = arg<suspend (URI) -> ConfirmFlowResource>(2)
            assertNull(run(session, flow))
            mapRedirectUriToResource(redirectUri)
        }

        val result = controller.getConfirmation(StateAuthentication("encoded-state"))

        assertEquals(redirectUri.toString(), result.redirectUrl)
        assertNull(result.action)
    }

    @Test
    fun `confirm - Marks the confirmation and returns the next-step redirect URL`() = runTest {
        val redirectUri = URI.create("https://auth.example.com/flow/mfa/enrollment?state=encoded-state")
        coEvery { confirmManager.markConfirmed(session) } returns mockk()
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect<SimpleFlowResource>(
                eq("encoded-state"), any(), any()
            )
        } coAnswers {
            val update = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Any>(1)
            val mapRedirectUriToResource = arg<suspend (URI) -> SimpleFlowResource>(2)
            assertSame(session, update(session, flow))
            mapRedirectUriToResource(redirectUri)
        }

        val result = controller.confirm(StateAuthentication("encoded-state"))

        assertEquals(redirectUri.toString(), result.redirectUrl)
        coVerify { confirmManager.markConfirmed(session) }
    }
}
