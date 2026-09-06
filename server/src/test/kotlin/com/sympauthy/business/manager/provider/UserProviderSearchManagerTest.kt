package com.sympauthy.business.manager.provider

import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
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
class UserProviderSearchManagerTest {

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @InjectMockKs
    lateinit var userProviderSearchManager: UserProviderSearchManager

    private val userId: UUID = UUID.randomUUID()
    private val linkedAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
    private val firstPage = PageParams(page = 0, size = 20)

    private fun providerUserInfo(
        providerId: String,
        linkDate: LocalDateTime = linkedAt
    ) = ProviderUserInfo(
        providerId = providerId,
        userId = userId,
        linkDate = linkDate,
        fetchDate = linkedAt,
        changeDate = linkedAt,
        sessionId = null,
        userInfo = RawProviderClaims(subject = "123456789012345678")
    )

    @Test
    fun `listUserProviders - Order by link date, then by provider identifier`() = runTest {
        // Two of the three were linked in the same instant, which is what the provider separates.
        val tiedFirst = providerUserInfo("discord")
        val tiedSecond = providerUserInfo("google")
        val earlier = providerUserInfo("apple", linkDate = linkedAt.minusDays(1))
        coEvery { providerClaimsManager.findByUserId(userId) } returns listOf(tiedSecond, tiedFirst, earlier)

        val result = userProviderSearchManager.listUserProviders(userId, firstPage)

        assertEquals(listOf("apple", "discord", "google"), result.items.map { it.providerId })
    }

    @Test
    fun `listUserProviders - Return the page the parameters name`() = runTest {
        coEvery { providerClaimsManager.findByUserId(userId) } returns listOf(
            providerUserInfo("apple"),
            providerUserInfo("discord"),
            providerUserInfo("google")
        )

        val result = userProviderSearchManager.listUserProviders(userId, PageParams(page = 1, size = 2))

        assertEquals(listOf("google"), result.items.map { it.providerId })
        assertEquals(3, result.total)
    }
}
