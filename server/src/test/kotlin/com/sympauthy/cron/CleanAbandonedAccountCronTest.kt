package com.sympauthy.cron

import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.config.model.CleanupConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CleanAbandonedAccountCronTest {

    @MockK
    lateinit var provisionalAccountManager: ProvisionalAccountManager

    @MockK
    lateinit var advancedConfig: EnabledAdvancedConfig

    @InjectMockKs
    lateinit var cron: CleanAbandonedAccountCron

    @Test
    fun `clean - Throws a failed sweep out of the scheduled method`() {
        batchSize(100)
        coEvery { provisionalAccountManager.deleteAbandoned(100) } throws IllegalStateException("delete refused")

        assertThrows<IllegalStateException> { cron.clean() }
    }

    @Test
    fun `clean - Returns only once the sweep of as many accounts as configured has run`() {
        batchSize(25)
        coEvery { provisionalAccountManager.deleteAbandoned(25) } returns ProvisionalAccountManager.CollectResult(
            deletedCount = 1,
            filledBatch = true
        )

        cron.clean()

        coVerify { provisionalAccountManager.deleteAbandoned(25) }
    }

    private fun batchSize(batchSize: Int) {
        every { advancedConfig.cleanup } returns CleanupConfig(batchSize = batchSize)
    }
}
