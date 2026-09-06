package com.sympauthy.cron

import com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CleanExpiredInteractiveFlowSessionCronTest {

    @MockK
    lateinit var interactiveFlowSessionCleaner: InteractiveFlowSessionCleaner

    @InjectMockKs
    lateinit var cron: CleanExpiredInteractiveFlowSessionCron

    @Test
    fun `clean - Throws a failed cleanup out of the scheduled method`() {
        coEvery { interactiveFlowSessionCleaner.clean() } throws IllegalStateException("delete refused")

        assertThrows<IllegalStateException> { cron.clean() }
    }

    @Test
    fun `clean - Returns only once the cleanup has run`() {
        coEvery { interactiveFlowSessionCleaner.clean() } returns InteractiveFlowSessionCleaner.CleanResult(
            sessionCount = 1,
            authorizationCodeCount = 0,
            validationCodesCount = 0,
            moreToClean = true
        )

        cron.clean()

        coVerify { interactiveFlowSessionCleaner.clean() }
    }
}
