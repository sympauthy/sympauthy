package com.sympauthy.business.manager.securitycontext

import com.sympauthy.data.model.SecurityContextEntity
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
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class SecurityContextCleanerTest {

    @MockK
    lateinit var securityContextRepository: SecurityContextRepository

    @InjectMockKs
    lateinit var cleaner: SecurityContextCleaner

    @Test
    fun `clean - Delete the contexts whose retention has run out`() = runTest {
        val expired = listOf(expiredContext(), expiredContext())
        val expiredIds = expired.map { it.id!! }
        coEvery { securityContextRepository.findExpired() } returns expired
        coEvery { securityContextRepository.deleteByIdIn(expiredIds) } returns expiredIds.size

        assertEquals(2, cleaner.clean())

        coVerify(exactly = 1) { securityContextRepository.deleteByIdIn(expiredIds) }
    }

    @Test
    fun `clean - Ask nothing of the database where nothing has run out`() = runTest {
        coEvery { securityContextRepository.findExpired() } returns emptyList()

        assertEquals(0, cleaner.clean())

        coVerify(exactly = 0) { securityContextRepository.deleteByIdIn(any()) }
    }

    private fun expiredContext() = SecurityContextEntity(
        fingerprint = UUID.randomUUID().toString(),
        firstSeenDate = LocalDateTime.now().minusDays(2),
        lastSeenDate = LocalDateTime.now().minusDays(2),
        observationCount = 1,
        expirationDate = LocalDateTime.now().minusDays(1)
    ).also { it.id = UUID.randomUUID() }
}
