package tournament.events.auth.business.manager.user

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tournament.events.auth.business.model.provider.ProviderUserInfo
import tournament.events.auth.business.model.user.CollectedUserInfo
import tournament.events.auth.business.model.user.RawUserInfo
import tournament.events.auth.business.model.user.RawUserInfoBuilder
import tournament.events.auth.business.model.user.StandardClaim
import java.time.LocalDate
import java.time.LocalDateTime.now
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class UserInfoMergerTest {

    @Test
    fun `merge - Providers in chronological order then collected`() {
        val userId = UUID.randomUUID()
        val builder = mockk<RawUserInfoBuilder>()
        val collectedUserInfo = mockk<CollectedUserInfo>()
        val providerUserInfo1 = mockk<ProviderUserInfo>()
        val providerUserInfo2 = mockk<ProviderUserInfo>()

        val merger = spyk(
            UserInfoMerger(
                userId = userId,
                collectedUserInfo = collectedUserInfo,
                providerUserInfoList = listOf(providerUserInfo1, providerUserInfo2)
            )
        )
        every { merger.getUpdatedAt(providerUserInfo1) } returns now()
        every { merger.getUpdatedAt(providerUserInfo2) } returns now().minusDays(1)
        every { merger.apply(any(), any<ProviderUserInfo>()) } returns builder
        every { merger.apply(any(), any<CollectedUserInfo>()) } returns builder

        merger.merge(builder)

        verifyOrder {
            merger.apply(builder, providerUserInfo2)
            merger.apply(builder, providerUserInfo1)
            merger.apply(builder, collectedUserInfo)
        }
    }

    @Test
    fun `apply - Copy all fields if everything is provided`() {
        val userId = UUID.randomUUID()
        val providerRawInfo = fullRawInfo(userId)
        val providerUserInfo = mockk<ProviderUserInfo>()

        every { providerUserInfo.userInfo } returns providerRawInfo

        val merger = UserInfoMerger(mockk(), mockk(), mockk())
        val rawInfo = merger.apply(
            RawUserInfoBuilder(userId),
            providerUserInfo
        ).build()

        assertEquals(providerRawInfo, rawInfo)
    }

    @Test
    fun `apply - Copy all fields if everything is collected`() {
        val userId = UUID.randomUUID()
        val collectedRawInfo = fullRawInfo(userId)
        val collectedUserInfo = CollectedUserInfo(
            userId = userId,
            collectedInfo = StandardClaim.values().toList(),
            info = collectedRawInfo
        )

        val merger = UserInfoMerger(mockk(), mockk(), mockk())
        val rawInfo = merger.apply(
            RawUserInfoBuilder(userId),
            collectedUserInfo
        ).build()

        assertEquals(collectedRawInfo, rawInfo)
    }

    private fun fullRawInfo(userId: UUID) = RawUserInfo(
        subject = userId.toString(),

        name = "name",
        givenName = "givenName",
        familyName = "familyName",
        middleName = "middleName",
        nickname = "nickname",

        preferredUsername = "preferredUsername",
        profile = "profile",
        picture = "picture",
        website = "website",

        email = "email",
        emailVerified = true,

        gender = "gender",
        birthDate = LocalDate.now(),

        zoneInfo = "zoneInfo",
        locale = "locale",

        phoneNumber = "phoneNumber",
        phoneNumberVerified = true,
        updatedAt = now()
    )
}