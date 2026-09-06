package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.manager.securitycontext.edge.CloudflareEdgeProviderProfile
import com.sympauthy.business.manager.securitycontext.edge.NoneEdgeProviderProfile
import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.SecurityContextField
import com.sympauthy.business.model.securitycontext.SecurityContextField.CITY
import com.sympauthy.business.model.securitycontext.SecurityContextField.CLIENT_IP
import com.sympauthy.business.model.securitycontext.observedRequestOf
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(MockKExtension::class)
class SecurityContextManagerTest {

    @Test
    fun `getObservedSecurityContext - Record the socket peer where the deployment named no proxy`() =
        runTest {
            val request = observedRequestOf(
                peer = "198.51.100.10",
                "CF-Connecting-IP" to "1.2.3.4",
                "X-Forwarded-For" to "1.2.3.4",
                "User-Agent" to "Mozilla/5.0"
            )

            val observed = managerOf().getObservedSecurityContext(request)

            assertEquals("198.51.100.10", observed.ip)
            assertEquals("Mozilla/5.0", observed.userAgent)
            assertNull(observed.geo.country)
            assertNull(observed.geo.region)
            assertNull(observed.geo.city)
        }

    @Test
    fun `getObservedSecurityContext - Answer no user agent where the caller sent none`() = runTest {
        val request = observedRequestOf(peer = "198.51.100.10")

        val observed = managerOf().getObservedSecurityContext(request)

        assertNull(observed.userAgent)
    }

    @Test
    fun `getObservedSecurityContext - Read the bound header, and leave the other fields on the profile`() =
        runTest {
            val request = observedRequestOf(
                peer = "10.0.0.1",
                "CF-Connecting-IP" to "198.51.100.10",
                "CF-IPCountry" to "FR",
                "cf-ipcity" to "Toulouse",
                "X-My-Proxy-City" to "Blagnac"
            )

            val observed = managerOf(
                profile = CloudflareEdgeProviderProfile(),
                headers = mapOf(CITY to "X-My-Proxy-City")
            ).getObservedSecurityContext(request)

            assertEquals("198.51.100.10", observed.ip)
            assertEquals("FR", observed.geo.country)
            assertEquals("Blagnac", observed.geo.city)
        }

    @Test
    fun `getObservedSecurityContext - Read an overridden header as it stands`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Forwarded-For" to "1.2.3.4, 198.51.100.10")

        val observed = managerOf(headers = mapOf(CLIENT_IP to "X-Forwarded-For"))
            .getObservedSecurityContext(request)

        assertEquals("1.2.3.4, 198.51.100.10", observed.ip)
    }

    @Test
    fun `getObservedSecurityContext - Read the header a deployment named where it named no proxy`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "X-My-Proxy-IP" to "198.51.100.10")

        val observed = managerOf(headers = mapOf(CLIENT_IP to "X-My-Proxy-IP"))
            .getObservedSecurityContext(request)

        assertEquals("198.51.100.10", observed.ip)
    }

    @Test
    fun `getObservedSecurityContext - Answer null where the overridden header did not arrive`() = runTest {
        val request = observedRequestOf(peer = "10.0.0.1", "CF-Connecting-IP" to "198.51.100.10")

        val observed = managerOf(
            profile = CloudflareEdgeProviderProfile(),
            headers = mapOf(CLIENT_IP to "X-My-Proxy-IP")
        ).getObservedSecurityContext(request)

        assertNull(observed.ip)
    }

    private fun managerOf(
        profile: EdgeProviderProfile = NoneEdgeProviderProfile(),
        headers: Map<SecurityContextField, String> = emptyMap()
    ): SecurityContextManager {
        val advancedConfig = mockk<EnabledAdvancedConfig>()
        every { advancedConfig.securityContext } returns SecurityContextConfig(
            profile = profile,
            headers = headers,
            unknownRetention = Duration.ofHours(24),
            knownRetention = Duration.ofDays(180)
        )
        return SecurityContextManager(advancedConfig)
    }
}
