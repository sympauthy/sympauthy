package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserMfaMethodResourceMapper
import com.sympauthy.api.resource.admin.AdminUserMfaEnrollmentInputResource
import com.sympauthy.api.resource.admin.AdminUserMfaMethodResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.DisabledMfaConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class AdminUserMfaControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var mfaMapper: AdminUserMfaMethodResourceMapper

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var clientRedirectUriManager: ClientRedirectUriManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    private fun controller(
        mfaConfig: MfaConfig = EnabledMfaConfig(totp = true, required = false)
    ) = AdminUserMfaController(
        userManager = userManager,
        totpManager = totpManager,
        mfaMapper = mfaMapper,
        interactiveAuthFlowSessionManager = interactiveAuthFlowSessionManager,
        clientRedirectUriManager = clientRedirectUriManager,
        clientManager = clientManager,
        mfaEnrollmentManager = mfaEnrollmentManager,
        engine = engine,
        stepUriMapper = stepUriMapper,
        uncheckedMfaConfig = mfaConfig,
    )

    private val userId: UUID = UUID.randomUUID()
    private val mfaId: UUID = UUID.randomUUID()
    private val confirmedDate: LocalDateTime = LocalDateTime.of(2026, 2, 10, 8, 45, 0)

    private fun mockEnrollment(
        id: UUID = mfaId,
        enrollmentUserId: UUID = userId
    ): TotpEnrollment = TotpEnrollment(
        id = id,
        userId = enrollmentUserId,
        secret = ByteArray(20),
        creationDate = confirmedDate,
        confirmedDate = confirmedDate
    )

    private fun mockResource(id: UUID = mfaId): AdminUserMfaMethodResource = AdminUserMfaMethodResource(
        mfaId = id,
        type = "totp",
        registeredAt = confirmedDate
    )

    @Test
    fun `listMfaMethods - Returns paginated list of confirmed enrollments`() = runTest {
        val enrollment = mockEnrollment()
        val resource = mockResource()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        every { mfaMapper.toResource(enrollment) } returns resource

        val result = controller().listMfaMethods(userId, null, null)

        assertEquals(1, result.mfaMethods.size)
        assertEquals(mfaId, result.mfaMethods[0].mfaId)
        assertEquals("totp", result.mfaMethods[0].type)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(1, result.total)
    }

    @Test
    fun `listMfaMethods - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller().listMfaMethods(userId, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `listMfaMethods - Returns empty list when user has no MFA`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        val result = controller().listMfaMethods(userId, null, null)

        assertTrue(result.mfaMethods.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun `listMfaMethods - Respects pagination parameters`() = runTest {
        val enrollments = (0 until 3).map { mockEnrollment(id = UUID.randomUUID()) }
        val resources = enrollments.map { mockResource(id = it.id) }
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns enrollments
        // The second page of two holds the third enrollment alone.
        every { mfaMapper.toResource(enrollments[2]) } returns resources[2]

        val result = controller().listMfaMethods(userId, 1, 2)

        assertEquals(1, result.mfaMethods.size)
        assertSame(resources[2], result.mfaMethods[0])
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    @Test
    fun `revokeMfaMethod - Deletes the enrollment`() = runTest {
        val enrollment = mockEnrollment()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns enrollment
        coEvery { totpManager.deleteEnrollment(enrollment) } returns Unit

        controller().revokeMfaMethod(userId, mfaId)

        coVerify { totpManager.deleteEnrollment(enrollment) }
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller().revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when MFA method not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller().revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `revokeMfaMethod - Returns 404 when enrollment belongs to different user`() = runTest {
        val otherUserId = UUID.randomUUID()
        val enrollment = mockEnrollment(enrollmentUserId = otherUserId)
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { totpManager.findConfirmedEnrollmentOrNull(mfaId) } returns enrollment

        val exception = assertThrows<LocalizedHttpException> {
            controller().revokeMfaMethod(userId, mfaId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `startEnrollment - Validates user, client and return URI, starts an admin-initiated session and returns the link`() =
        runTest {
            val client = mockk<Client>()
            val returnUri = URI.create("https://client.example.com/done")
            val flow = mockk<InteractiveFlow>()
            val session = mockk<OnGoingInteractiveFlowSession>()
            val steppedSession = mockk<OnGoingInteractiveFlowSession>()
            val redirectUri = URI.create("https://auth.example.com/flow/confirm?state=abc")

            coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
            coEvery { clientManager.findClientByIdOrNull("client-id") } returns client
            every {
                clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/done", recoverable = true)
            } returns returnUri
            coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
            // Admin-initiated: the initiating client id must be null (rendered as "an administrator"). Keying
            // the stub on null means the test only reaches the assertion when the controller passes null.
            coEvery {
                mfaEnrollmentManager.startMfaEnrollmentSession(userId, returnUri, flow, null, null)
            } returns session
            coEvery { engine.advance(session) } returns
                InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.Confirm)
            coEvery {
                stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.Confirm)
            } returns redirectUri

            val result = controller().startEnrollment(
                userId,
                AdminUserMfaEnrollmentInputResource(
                    clientId = "client-id",
                    returnUri = "https://client.example.com/done"
                )
            )

            assertEquals(redirectUri.toString(), result.redirectUrl)
        }

    @Test
    fun `startEnrollment - Validates and forwards the optional cancel URI`() = runTest {
        val client = mockk<Client>()
        val returnUri = URI.create("https://client.example.com/done")
        val cancelUri = URI.create("https://client.example.com/cancelled")
        val flow = mockk<InteractiveFlow>()
        val session = mockk<OnGoingInteractiveFlowSession>()
        val steppedSession = mockk<OnGoingInteractiveFlowSession>()
        val redirectUri = URI.create("https://auth.example.com/flow/confirm?state=abc")

        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull("client-id") } returns client
        every {
            clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/done", recoverable = true)
        } returns returnUri
        every {
            clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/cancelled", recoverable = true)
        } returns cancelUri
        coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
        // The stub only matches (and thus drives the redirect) when the validated cancel URI is threaded through.
        coEvery {
            mfaEnrollmentManager.startMfaEnrollmentSession(userId, returnUri, flow, null, cancelUri)
        } returns session
        coEvery { engine.advance(session) } returns
            InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.Confirm)
        coEvery {
            stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.Confirm)
        } returns redirectUri

        val result = controller().startEnrollment(
            userId,
            AdminUserMfaEnrollmentInputResource(
                clientId = "client-id",
                returnUri = "https://client.example.com/done",
                cancelUri = "https://client.example.com/cancelled"
            )
        )

        assertEquals(redirectUri.toString(), result.redirectUrl)
    }

    @Test
    fun `startEnrollment - Fails with mfa_disabled and starts no session when MFA is disabled`() = runTest {
        val exception = assertThrows<BusinessException> {
            controller(mockk<DisabledMfaConfig>()).startEnrollment(
                userId,
                AdminUserMfaEnrollmentInputResource(
                    clientId = "client-id",
                    returnUri = "https://client.example.com/done"
                )
            )
        }

        assertEquals("admin.users.mfa.enrollment.mfa_disabled", exception.detailsId)
        coVerify(exactly = 0) {
            mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startEnrollment - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller().startEnrollment(
                userId,
                AdminUserMfaEnrollmentInputResource(
                    clientId = "client-id",
                    returnUri = "https://client.example.com/done"
                )
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startEnrollment - Returns 404 when client not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull("client-id") } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller().startEnrollment(
                userId,
                AdminUserMfaEnrollmentInputResource(
                    clientId = "client-id",
                    returnUri = "https://client.example.com/done"
                )
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any(), any())
        }
    }
}
