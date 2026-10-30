package com.sympauthy.api.controller.openid

import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.security.UserAuthentication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class OpenIdUserInfoControllerTest {

    @MockK
    lateinit var claimAclManager: ClaimAclManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var userInfoMapper: UserInfoResourceMapper

    @InjectMockKs
    lateinit var controller: OpenIdUserInfoController

    private val userId: UUID = UUID.randomUUID()

    // Another audience than the grant's: the read below is asserted against the grant's, so it cannot be
    // answering for the client's.
    private val client = Client(
        id = "client",
        secret = null,
        audience = Audience(id = CLIENT_AUDIENCE, tokenAudience = CLIENT_AUDIENCE),
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    @Test
    fun `getUserInfo - Read the claims of the audience the token was issued for`() = runTest {
        val doors = mockk<ClaimAclTokenDoors>()
        val claims = listOf(mockk<CollectedClaim>())
        val resource = mockk<UserInfoResource>()

        every { claimAclManager.getTokenDoors(any(), any()) } returns doors
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByPerson(userId, AUDIENCE, doors)
        } returns claims
        coEvery { userInfoMapper.toResource(userId, claims, RequestedClaims.NONE) } returns resource

        val result = controller.getUserInfo(authentication())

        assertSame(resource, result)
    }

    /** The person's authentication, through a bearer token issued to the [client] under a grant of [userId]. */
    private fun authentication(): UserAuthentication {
        val grant = mockk<Grant> {
            every { userId } returns this@OpenIdUserInfoControllerTest.userId
            every { audienceId } returns AUDIENCE
            every { authenticationDate } returns null
            every { requestedClaims } returns RequestedClaims.NONE
        }
        val token = mockk<AuthenticationToken> {
            every { this@mockk.grant } returns grant
            every { dpopJkt } returns null
        }
        return UserAuthentication(token, client, emptyList(), emptyList())
    }

    private companion object {
        const val AUDIENCE = "default"
        const val CLIENT_AUDIENCE = "acting-client"
    }
}
