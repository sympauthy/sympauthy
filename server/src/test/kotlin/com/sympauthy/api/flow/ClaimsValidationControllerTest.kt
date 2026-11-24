package com.sympauthy.api.flow

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.flow.mapper.ClaimsValidationFlowResultResourceMapper
import com.sympauthy.api.flow.mapper.ClientReferenceResourceMapper
import com.sympauthy.api.flow.resource.ClaimValidationInputResource
import com.sympauthy.api.flow.resource.ClaimsValidationFlowResource
import com.sympauthy.api.flow.resource.ResendClaimsValidationCodeResultResource
import com.sympauthy.api.flow.resource.ResendClaimsValidationInputResource
import com.sympauthy.api.flow.resource.SimpleFlowResource
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionClaimValidationManager
import com.sympauthy.business.flow.model.InteractiveFlow
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.securitycontext.edge.model.observedRequestOf
import com.sympauthy.business.user.model.User
import com.sympauthy.business.validationcode.model.ValidationCodeMedia
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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

    @MockK
    lateinit var clientMapper: ClientReferenceResourceMapper

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    private val controller by lazy {
        ClaimsValidationController(
            claimValidationManager,
            interactiveAuthFlowSessionControllerUtil,
            resourceMapper,
            clientMapper,
            stepUriMapper
        )
    }

    private val session = mockk<OnGoingInteractiveFlowSession>()
    private val flow = mockk<InteractiveFlow>()
    private val user = mockk<User>()

    @Test
    fun `getValidationCodeToCollectForMedia - Refuses the Kotlin name of a media before reading the session`() =
        runTest {
            val exception = assertThrows<LocalizedHttpException> {
                controller.getValidationCodeToCollectForMedia(
                    StateAuthentication("encoded-state"),
                    observedRequestOf(),
                    "EMAIL"
                )
            }

            assertUnknownMedia(exception, media = "EMAIL")
            coVerify(exactly = 0) {
                interactiveAuthFlowSessionControllerUtil
                    .fetchOnGoingSessionWithUserThenRunAndRedirect<Any, ClaimsValidationFlowResource>(
                        any(), any(), any(), any(), any()
                    )
            }
        }

    @Test
    fun `validate - Validates the code through the media named`() = runTest {
        stubUpdate()
        coEvery {
            claimValidationManager.validateClaimsByCode(session, user, ValidationCodeMedia.EMAIL, "123456")
        } just Runs

        val result = controller.validate(
            StateAuthentication("encoded-state"),
            observedRequestOf(),
            ClaimValidationInputResource(media = "email", code = "123456")
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
        coVerify(exactly = 0) { claimValidationManager.validateClaimsByCode(any(), any(), any(), any()) }
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
            interactiveAuthFlowSessionControllerUtil
                .fetchOnGoingSessionWithUserThenUpdateAndRedirect<SimpleFlowResource>(
                    eq("encoded-state"), any(), any(), any()
                )
        } coAnswers {
            val update =
                arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> InteractiveFlowSession>(2)
            val mapRedirectUriToResource = arg<suspend (URI) -> SimpleFlowResource>(3)
            update(session, flow, user)
            mapRedirectUriToResource(URI.create("https://example.com/next"))
        }
    }

    private fun assertUnknownMedia(exception: LocalizedHttpException, media: String = "pigeon") {
        assertEquals(BAD_REQUEST, exception.status)
        assertTrue(exception.recoverable)
        assertEquals("flow.claim_validation.unknown_media", exception.detailsId)
        assertEquals("description.flow.claim_validation.unknown_media", exception.descriptionId)
        assertEquals(media, exception.values["media"])
        assertEquals("email, sms", exception.values["supportedValues"])
    }
}
