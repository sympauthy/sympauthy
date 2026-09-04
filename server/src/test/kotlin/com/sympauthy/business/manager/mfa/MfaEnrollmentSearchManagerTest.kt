package com.sympauthy.business.manager.mfa

import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.page.PageParams
import io.mockk.coEvery
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
class MfaEnrollmentSearchManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var mfaEnrollmentSearchManager: MfaEnrollmentSearchManager

    private val userId: UUID = UUID.randomUUID()
    private val confirmedDate: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
    private val firstPage = PageParams(page = 0, size = 20)

    private fun id(last: Int): UUID = UUID.fromString("00000000-0000-0000-0000-00000000000$last")

    private fun enrollment(
        id: UUID,
        confirmedDate: LocalDateTime = this.confirmedDate,
        creationDate: LocalDateTime = this.confirmedDate
    ) = TotpEnrollment(
        id = id,
        userId = userId,
        secret = ByteArray(20),
        creationDate = creationDate,
        confirmedDate = confirmedDate
    )

    @Test
    fun `listConfirmedEnrollments - Order by confirmation date, then by identifier`() = runTest {
        // Two of the three were confirmed in the same instant, which is what the identifier separates.
        val tiedFirst = enrollment(id(1))
        val tiedSecond = enrollment(id(2))
        val earlier = enrollment(id(3), confirmedDate = confirmedDate.minusDays(1))
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(tiedSecond, tiedFirst, earlier)

        val result = mfaEnrollmentSearchManager.listConfirmedEnrollments(userId, firstPage)

        assertEquals(listOf(earlier.id, tiedFirst.id, tiedSecond.id), result.items.map { it.id })
    }

    @Test
    fun `listConfirmedEnrollments - Order by the confirmation rather than the enrollment`() = runTest {
        // Started last week and confirmed today, so it belongs at the tail rather than in a walked page.
        val startedEarlier = enrollment(
            id(1), confirmedDate = confirmedDate.plusDays(1), creationDate = confirmedDate.minusDays(7)
        )
        val confirmedEarlier = enrollment(id(2))
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(startedEarlier, confirmedEarlier)

        val result = mfaEnrollmentSearchManager.listConfirmedEnrollments(userId, firstPage)

        assertEquals(listOf(confirmedEarlier.id, startedEarlier.id), result.items.map { it.id })
    }

    @Test
    fun `listConfirmedEnrollments - Return the page the parameters name`() = runTest {
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(
            enrollment(id(1)),
            enrollment(id(2)),
            enrollment(id(3))
        )

        val result = mfaEnrollmentSearchManager.listConfirmedEnrollments(userId, PageParams(page = 1, size = 2))

        assertEquals(listOf(id(3)), result.items.map { it.id })
        assertEquals(3, result.total)
    }
}
