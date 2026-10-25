package com.sympauthy.business.manager

import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.data.repository.CollectedClaimRepository
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
class GeneratedClaimsManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @InjectMockKs
    lateinit var manager: GeneratedClaimsManager

    private val userId: UUID = UUID.randomUUID()

    private val user = mockk<User> { every { id } returns userId }

    @Test
    fun `computeValuesReadableByClient - Leave out a generated claim whose client read door is shut`() = runTest {
        val subject = generatedClaim(OpenIdConnectClaimId.SUB)
        val updatedAt = generatedClaim(OpenIdConnectClaimId.UPDATED_AT)
        every { claimManager.listEnabledClaims() } returns listOf(subject, updatedAt)
        val doors = mockk<ClaimAclTokenDoors> {
            every { canBeReadByClient(subject) } returns true
            every { canBeReadByClient(updatedAt) } returns false
        }

        // The repository is left unstubbed: reaching the assertion is proof `updated_at` was not computed.
        val result = manager.computeValuesReadableByClient(user, doors)

        assertEquals(mapOf<String, Any?>(OpenIdConnectClaimId.SUB to userId.toString()), result)
    }

    private fun generatedClaim(claimId: String): EnabledClaim = mockk {
        every { id } returns claimId
    }
}
