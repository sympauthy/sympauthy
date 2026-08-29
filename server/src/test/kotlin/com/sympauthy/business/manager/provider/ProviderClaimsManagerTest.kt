package com.sympauthy.business.manager.provider

import com.sympauthy.business.mapper.ProviderUserInfoMapper
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.repository.ProviderUserInfoRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ProviderClaimsManagerTest {

    @MockK
    lateinit var userInfoRepository: ProviderUserInfoRepository

    @MockK
    lateinit var userInfoMapper: ProviderUserInfoMapper

    @InjectMockKs
    lateinit var manager: ProviderClaimsManager

    private val userId: UUID = UUID.randomUUID()
    private val claims = RawProviderClaims(subject = "123456789012345678")

    /**
     * Derived from the clock rather than a calendar date, so an assertion that a freshly-taken date is
     * after it tests the subject rather than the machine the suite runs on.
     */
    private val linkDate: LocalDateTime = LocalDateTime.now().minusMonths(6)

    private fun mockProvider(id: String = "discord") = mockk<EnabledProvider> {
        every { this@mockk.id } returns id
    }

    private fun mockExistingUserInfo() = ProviderUserInfo(
        providerId = "discord",
        userId = userId,
        linkDate = linkDate,
        fetchDate = linkDate,
        changeDate = linkDate,
        userInfo = claims
    )

    @Test
    fun `saveUserInfo - Records the link date as the moment the link is created`() = runTest {
        val entity = mockk<ProviderUserInfoEntity>()
        val linkDateSlot = slot<LocalDateTime>()
        val fetchDateSlot = slot<LocalDateTime>()
        every {
            userInfoMapper.toEntity(any(), any(), any(), capture(linkDateSlot), capture(fetchDateSlot), any())
        } returns entity
        coEvery { userInfoRepository.save(entity) } returns entity
        every { userInfoMapper.toProviderUserInfo(entity) } returns mockExistingUserInfo()

        val before = LocalDateTime.now()
        manager.saveUserInfo(mockProvider(), userId, claims)

        assertTrue(linkDateSlot.captured >= before)
        assertEquals(linkDateSlot.captured, fetchDateSlot.captured)
    }

    @Test
    fun `refreshUserInfo - Keeps the link date of the link being refreshed`() = runTest {
        val existingUserInfo = mockExistingUserInfo()
        val entity = mockk<ProviderUserInfoEntity>()
        val linkDateSlot = slot<LocalDateTime>()
        val fetchDateSlot = slot<LocalDateTime>()
        every {
            userInfoMapper.toEntity(any(), any(), any(), capture(linkDateSlot), capture(fetchDateSlot), any())
        } returns entity
        coEvery { userInfoRepository.update(entity) } returns entity

        manager.refreshUserInfo(existingUserInfo, claims.copy(nickname = "renamed"))

        assertEquals(linkDate, linkDateSlot.captured)
        assertTrue(fetchDateSlot.captured > existingUserInfo.fetchDate)
    }

    @Test
    fun `refreshUserInfo - Moves the change date only when the claims differ`() = runTest {
        val existingUserInfo = mockExistingUserInfo()
        val entity = mockk<ProviderUserInfoEntity>()
        val changeDateSlot = slot<LocalDateTime>()
        every {
            userInfoMapper.toEntity(any(), any(), any(), any(), any(), capture(changeDateSlot))
        } returns entity
        coEvery { userInfoRepository.update(entity) } returns entity

        manager.refreshUserInfo(existingUserInfo, claims)
        assertEquals(existingUserInfo.changeDate, changeDateSlot.captured)

        manager.refreshUserInfo(existingUserInfo, claims.copy(nickname = "renamed"))
        assertTrue(changeDateSlot.captured > existingUserInfo.changeDate)
    }
}
