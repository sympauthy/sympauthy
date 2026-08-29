package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.user.User
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class InteractiveFlowSessionMfaChallengeManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionMfaChallengeManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }

    @Test
    fun `getChallengeRoutingResult - single enrolled method - auto-redirects to TOTP challenge`() = runTest {
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(mockk())

        val result = manager.getChallengeRoutingResult(user)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpChallenge), result)
    }

    @Test
    fun `getChallengeRoutingResult - several enrolled methods - offers a choice without skip`() = runTest {
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(mockk(), mockk())

        val result = manager.getChallengeRoutingResult(user)

        assertEquals(
            MfaMethodSelection(
                methods = listOf(AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpChallenge)),
                skippable = false
            ),
            result
        )
    }
}
