package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.flow.ClaimsValidationFlowResultResourceMapper
import com.sympauthy.api.resource.flow.ClaimValidationInputResource
import com.sympauthy.api.resource.flow.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.resource.flow.ResendClaimsValidationInputResource
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionClaimValidationManager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.security.observedRequestOf
import com.sympauthy.business.model.user.User
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.Runs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class ClaimsValidationControllerTest {

    @MockK
    lateinit var claimValidationManager: InteractiveAuthFlowSessionClaimValidationManager

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    @MockK
    lateinit var resourceMapper: ClaimsValidationFlowResultResourceMapper

    private val controller by lazy {
        ClaimsValidationController(claimValidationManager, interactiveAuthFlowSessionControllerUtil, resourceMapper)
    }

    private val session = mockk<OnGoingInteractiveFlowSession>()
    private val flow = mockk<InteractiveFlow>()
    private val user = mockk<User>()

    @Test
    fun `validate - Validates the code through the media named`() = runTest {
        stubUpdate()
        coEvery { claimValidationManager.validateClaimsByCode(session, ValidationCodeMedia.EMAIL, "123456") } just Runs

        val result = controller.validate(
            StateAuthentication("encoded-state"),
            observedRequestOf(),
            ClaimValidationInputResource(media = "EMAIL", code = "123456")
        )

        assertEquals("https://example.com/next", result.redirectUrl)
    }

    @Test
    fun `validate - Refuses a media naming none before validating any code`() = runTest {
        stubUpdate()

        val exception = assertThrows<LocalizedHttpException> {
            controller.validate(
                StateAuthentication("encoded-state"),
                observedRequestOf(),
                ClaimValidationInputResource(media = "pigeon", code = "123456")
            )
        }

        assertUnknownMedia(exception)
        coVerify(exactly = 0) { claimValidationManager.validateClaimsByCode(any(), any(), any()) }
    }

    @Test
    fun `resendValidationCodes - Refuses a media naming none before sending any code`() = runTest {
        coEvery {
            interactiveAuthFlowSessionControllerUtil
                .fetchOnGoingSessionWithUserThenRun<ResendClaimsValidationCodeResultResource>(
                    eq("encoded-state"), any(), any()
                )
        } coAnswers {
            val run = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) ->
            ResendClaimsValidationCodeResultResource>(2)
            run(session, flow, user)
        }

        val exception = assertThrows<LocalizedHttpException> {
            controller.resendValidationCodes(
                StateAuthentication("encoded-state"),
                observedRequestOf(),
                ResendClaimsValidationInputResource(media = "pigeon")
            )
        }

        assertUnknownMedia(exception)
        coVerify(exactly = 0) { claimValidationManager.resendValidationCode(any(), any(), any()) }
    }

    private fun stubUpdate() {
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect<SimpleFlowResource>(
                eq("encoded-state"), any(), any(), any()
            )
        } coAnswers {
            val update = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> InteractiveFlowSession>(2)
            val mapRedirectUriToResource = arg<suspend (URI) -> SimpleFlowResource>(3)
            update(session, flow)
            mapRedirectUriToResource(URI.create("https://example.com/next"))
        }
    }

    private fun assertUnknownMedia(exception: LocalizedHttpException) {
        assertEquals(BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("flow.claim_validation.unknown_media", exception.detailsId)
        assertEquals("description.flow.claim_validation.unknown_media", exception.descriptionId)
        assertEquals("pigeon", exception.values["media"])
        assertEquals("EMAIL, SMS", exception.values["supportedValues"])
    }
}
