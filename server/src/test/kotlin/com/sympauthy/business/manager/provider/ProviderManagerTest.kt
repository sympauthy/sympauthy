package com.sympauthy.business.manager.provider

import com.sympauthy.business.model.provider.DisabledProvider
import com.sympauthy.client.openidconnect.OpenIdConnectDiscoveryClient
import com.sympauthy.config.model.EnabledProvidersConfig
import com.sympauthy.config.model.ProviderConfig
import com.sympauthy.config.model.ProviderOpenIdConnectInputConfig
import com.sympauthy.exception.LocalizedException
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.ConnectException
import java.net.URI

/**
 * Resolving a provider at startup, and telling a provider that cannot be reached apart from one that answers
 * with something it cannot be resolved from.
 */
@ExtendWith(MockKExtension::class)
class ProviderManagerTest {

    private val discoveryClient = mockk<OpenIdConnectDiscoveryClient>()

    private val conditionMonitor = ConditionMonitor()

    private val issuer = URI.create("https://accounts.example.com")

    private val manager = ProviderManager(
        providersConfig = EnabledProvidersConfig(
            listOf(
                ProviderConfig(
                    id = "example",
                    name = "Example",
                    auth = ProviderOpenIdConnectInputConfig(
                        clientId = "client",
                        clientSecret = "secret",
                        scopes = listOf("openid"),
                        issuer = issuer
                    ),
                    userInfo = null
                )
            )
        ),
        openIdConnectDiscoveryClient = discoveryClient,
        remoteCallMonitor = RemoteCallMonitor(conditionMonitor, OpenTelemetry.noop())
    )

    @AfterEach
    fun close() {
        manager.close()
    }

    private fun discoveryFailed(cause: Throwable) = LocalizedException(
        detailsId = "provider.openid_connect.discovery_failed",
        values = mapOf("issuer" to issuer.toString()),
        throwable = cause
    )

    @Test
    fun `init - Starts a provider that cannot be reached disabled and its condition degraded`() = runTest {
        coEvery { discoveryClient.fetchDiscovery(issuer) } throws discoveryFailed(ConnectException("Refused"))

        manager.init()

        assertTrue(manager.listProviders().single() is DisabledProvider)
        assertTrue(conditionMonitor.isDegraded(Condition.Provider("example")))
    }

    @Test
    fun `init - Disables a provider answering with something unusable without degrading its condition`() =
        runTest {
            coEvery { discoveryClient.fetchDiscovery(issuer) } throws discoveryFailed(
                HttpClientResponseException("Not Found", HttpResponse.notFound<Any>())
            )

            manager.init()

            assertTrue(manager.listProviders().single() is DisabledProvider)
            assertFalse(conditionMonitor.isDegraded(Condition.Provider("example")))
        }
}
