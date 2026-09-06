package com.sympauthy.api.controller.openid

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.UserInfoResourceMapper
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.securitycontext.AccessReviewManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.AccessReviewReason
import com.sympauthy.security.UserAuthentication
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.micronaut.http.simple.SimpleHttpRequest
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

/**
 * What the UserInfo endpoint does with each answer a client's access review can give. What the review
 * itself decides is [com.sympauthy.business.manager.securitycontext.AccessReviewManager]'s.
 */
@ExtendWith(MockKExtension::class)
class OpenIdUserInfoControllerTest {

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var userInfoMapper: UserInfoResourceMapper

    @MockK
    lateinit var accessReviewManager: AccessReviewManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var tokenManager: TokenManager

    @InjectMockKs
    lateinit var controller: OpenIdUserInfoController

    private val userId = UUID.randomUUID()

    private val httpRequest = SimpleHttpRequest<Any>(HttpMethod.GET, "http://198.51.100.10/", null)

    @Test
    fun `getUserInfo - Serve the claims where the client allowed the place`() = runTest {
        val resource = mockk<UserInfoResource>()
        stubReview(AccessReviewDecision.ALLOW)
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, any()) } returns emptyList()
        coEvery { userInfoMapper.toResource(userId, emptyList()) } returns resource

        val served = controller.getUserInfo(authentication(), httpRequest)

        assertSame(resource, served)
    }

    @Test
    fun `getUserInfo - Refuse where the client refused the place`() = runTest {
        stubReview(AccessReviewDecision.DENY)

        val exception = assertThrows<LocalizedHttpException> {
            controller.getUserInfo(authentication(), httpRequest)
        }

        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals("userinfo.access_review_denied", exception.detailsId)
        coVerify(exactly = 0) { tokenManager.revokeSessionTokens(any()) }
    }

    @Test
    fun `getUserInfo - Revoke the whole sign-in where the client asked for it`() = runTest {
        val sessionId = UUID.randomUUID()
        stubReview(AccessReviewDecision.REVOKE_SESSION, sessionId = sessionId)
        coJustRun { tokenManager.revokeSessionTokens(sessionId) }

        assertThrows<LocalizedHttpException> {
            controller.getUserInfo(authentication(sessionId), httpRequest)
        }

        coVerify(exactly = 1) { tokenManager.revokeSessionTokens(sessionId) }
    }

    @Test
    fun `getUserInfo - Serve a token whose client this deployment no longer configures`() = runTest {
        val resource = mockk<UserInfoResource>()
        coEvery { clientManager.findClientByIdOrNull("my-app") } returns null
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, any()) } returns emptyList()
        coEvery { userInfoMapper.toResource(userId, emptyList()) } returns resource

        val served = controller.getUserInfo(authentication(), httpRequest)

        assertSame(resource, served)

        coVerify(exactly = 0) { accessReviewManager.reviewAccess(any(), any(), any(), any()) }
    }

    private fun stubReview(decision: AccessReviewDecision, sessionId: UUID? = null) {
        val client = mockk<Client>()
        coEvery { clientManager.findClientByIdOrNull("my-app") } returns client
        coEvery {
            accessReviewManager.reviewAccess(client, userId, AccessReviewReason.USERINFO, any())
        } returns decision
    }

    private fun authentication(sessionId: UUID? = null) = UserAuthentication(
        authenticationToken = mockk<AuthenticationToken> {
            every { this@mockk.userId } returns this@OpenIdUserInfoControllerTest.userId
            every { clientId } returns "my-app"
            if (sessionId != null) {
                every { this@mockk.sessionId } returns sessionId
            }
        },
        consentedScopes = emptyList(),
        grantedScopes = emptyList()
    )
}
