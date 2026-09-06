package com.sympauthy.business.manager.securitycontext

import com.sympauthy.data.repository.SecurityContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SecurityContextCleanerTest {

    @MockK
    lateinit var securityContextRepository: SecurityContextRepository

    @InjectMockKs
    lateinit var cleaner: SecurityContextCleaner

    @Test
    fun `clean - Answer how many contexts the sweep deleted`() = runTest {
        coEvery { securityContextRepository.deleteExpired() } returns 2

        assertEquals(2, cleaner.clean())
    }

    @Test
    fun `clean - Read no row to delete one`() = runTest {
        coEvery { securityContextRepository.deleteExpired() } returns 0

        assertEquals(0, cleaner.clean())

        coVerify(exactly = 0) { securityContextRepository.findPastByUserId(any(), any(), any()) }
    }
}
