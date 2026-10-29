package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.mapper.flow.TimeZoneResourceMapper
import com.sympauthy.api.resource.provider.TimeZoneResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.TimeZoneClaimValueProvider
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.security.observedRequestOf
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.TimeZone
import com.sympauthy.security.StateAuthentication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class TimeZoneControllerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var timeZoneProvider: TimeZoneClaimValueProvider

    @MockK
    lateinit var timeZoneResourceMapper: TimeZoneResourceMapper

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    private val controller by lazy {
        TimeZoneController(
            claimManager, timeZoneProvider, timeZoneResourceMapper, interactiveAuthFlowSessionControllerUtil
        )
    }

    private val observed = observedRequestOf()

    @Test
    fun `listTimeZones - List the time zones of the claim once the state resolves to an ongoing session`() = runTest {
        val claim = mockk<EnabledClaim>()
        val timeZones = listOf(mockk<TimeZone>())
        val resources = listOf(mockk<TimeZoneResource>())
        // Matching only the observed request the controller bound is what proves it was threaded through.
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRun<List<TimeZoneResource>>(
                eq("encoded-state"), refEq(observed), any()
            )
        } coAnswers {
            arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> List<TimeZoneResource>>(2)(
                mockk(), mockk()
            )
        }
        every { claimManager.findByIdOrNull("zoneinfo") } returns claim
        coEvery { timeZoneProvider.listAvailableTimezones(claim) } returns timeZones
        every { timeZoneResourceMapper.toResources(timeZones) } returns resources

        val result = controller.listTimeZones(StateAuthentication("encoded-state"), observed, "zoneinfo")

        assertSame(resources, result)
    }

    @Test
    fun `listTimeZones - Read no claim when the state resolves to no ongoing session`() = runTest {
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRun<List<TimeZoneResource>>(
                any(), any(), any()
            )
        } throws businessExceptionOf("auth.interactive_flow_session.validate.wrong_signature")

        val exception = assertThrows<BusinessException> {
            controller.listTimeZones(StateAuthentication("forged-state"), observed, "zoneinfo")
        }

        assertEquals("auth.interactive_flow_session.validate.wrong_signature", exception.detailsId)
        verify { claimManager wasNot Called }
    }
}
