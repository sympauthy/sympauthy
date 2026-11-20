package com.sympauthy.business.manager.provider

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.provider.openidconnect.ProviderIdTokenClaimsExtractor
import com.sympauthy.business.manager.provider.openidconnect.ProviderIdTokenManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2Tokens
import com.sympauthy.client.UserInfoEndpointClient
import com.sympauthy.monitoring.RemoteCallMonitor
import com.sympauthy.monitoring.telemetry.Outcome
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ProviderClaimsResolverTest {

    @MockK
    lateinit var providerIdTokenManager: ProviderIdTokenManager

    @MockK
    lateinit var providerIdTokenClaimsExtractor: ProviderIdTokenClaimsExtractor

    @MockK
    lateinit var userInfoEndpointClient: UserInfoEndpointClient

    @MockK
    lateinit var remoteCallMonitor: RemoteCallMonitor

    @InjectMockKs
    lateinit var resolver: ProviderClaimsResolver

    private val provider = EnabledProvider(
        id = "test-provider",
        name = "Test Provider",
        userInfo = mockk<ProviderUserInfoConfig>(),
        auth = mockk<ProviderOAuth2Config>()
    )

    /** Tokens carrying no ID token, so the claims are read from the userinfo endpoint. */
    private val tokens = ProviderOAuth2Tokens(accessToken = "access-token", refreshToken = null)

    /** What the monitor's judge was handed, which is what the provider's condition and the metric count. */
    private val judgedFailures = mutableListOf<Throwable>()

    private fun stubUserInfoEndpointFailing(failure: Exception) {
        coEvery {
            remoteCallMonitor.observe<Map<*, *>>(any(), any(), any(), any())
        } coAnswers {
            try {
                arg<suspend () -> Map<*, *>>(3).invoke()
            } catch (e: Exception) {
                arg<(Throwable) -> Outcome>(2).invoke(e)
                judgedFailures += e
                throw e
            }
        }
        coEvery { userInfoEndpointClient.fetchUserInfo(any(), any()) } throws failure
    }

    @Test
    fun `resolveClaims - Fails the session where the userinfo endpoint refuses the access token`() = runTest {
        stubUserInfoEndpointFailing(
            HttpClientResponseException("Unauthorized", HttpResponse.status<Any>(HttpStatus.UNAUTHORIZED))
        )

        val failure = assertThrows<BusinessException> { resolver.resolveClaims(provider, tokens) }

        assertEquals("provider.user_info.failed", failure.detailsId)
        assertFalse(failure.recoverable)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.recommendedStatus)
        assertEquals("test-provider", failure.values["providerId"])
    }

    @Test
    fun `resolveClaims - Has the provider's failure judged as it was thrown, not as the flow reports it`() = runTest {
        val thrown = ReadTimeoutException.TIMEOUT_EXCEPTION
        stubUserInfoEndpointFailing(thrown)

        val failure = assertThrows<BusinessException> { resolver.resolveClaims(provider, tokens) }

        assertEquals("provider.user_info.failed", failure.detailsId)
        assertSame(thrown, judgedFailures.single())
        assertSame(thrown, failure.cause)
    }
}
