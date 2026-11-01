package com.sympauthy.business.manager.branding

import com.sympauthy.business.model.branding.BrandImage
import com.sympauthy.business.model.branding.BrandImageName
import com.sympauthy.business.model.branding.BrandImageName.ICON
import com.sympauthy.business.model.branding.BrandImageName.LOGO
import com.sympauthy.business.model.branding.BrandImageName.LOGO_DARK
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.client.branding.BrandImageClient
import com.sympauthy.config.model.DisabledUIConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.LocalBrandImage
import com.sympauthy.config.model.ThemeConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import com.sympauthy.monitoring.RemoteCallMonitor
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.serde.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.ConnectException
import java.net.URI
import java.time.Instant

class BrandManagerTest {

    private val client = mockk<BrandImageClient>()
    private val conditionMonitor = ConditionMonitor()
    private val uri = URI.create("https://cdn.example/logo.svg")
    private val svg = "<svg>deployment</svg>".toByteArray()
    private val otherSvg = "<svg>dark</svg>".toByteArray()
    private val managers = mutableListOf<BrandManager>()

    private fun manager(config: UIConfig = config()): BrandManager = BrandManager(
        config,
        client,
        RemoteCallMonitor(conditionMonitor, OpenTelemetry.noop()),
        ResourceResolver(),
        BrandStylesheetRenderer(ResourceResolver(), ObjectMapper.getDefault())
    ).also(managers::add)

    private fun config(
        logo: ByteArray? = null,
        logoDark: ByteArray? = null,
        displayName: String? = null,
        theme: ThemeConfig = ThemeConfig.DEFAULT
    ) = EnabledUIConfig(
        displayName = displayName,
        icon = null,
        logo = logo?.let { LocalBrandImage(BrandImage.ofOrNull(it)!!) },
        logoDark = logoDark?.let { LocalBrandImage(BrandImage.ofOrNull(it)!!) },
        theme = theme
    )

    private fun default(name: BrandImageName) = manager().getImage(name).content

    @AfterEach
    fun close() {
        managers.forEach { it.close() }
    }

    @Test
    fun `getImage - Answers SympAuthy's marks where nothing is configured`() {
        val manager = manager().apply { init() }

        BrandImageName.entries.forEach { name ->
            val asset = manager.getImage(name)
            assertTrue(asset.mediaType.startsWith("image/"))
            assertTrue(asset.content.isNotEmpty())
        }
        assertFalse(manager.getImage(LOGO).content.contentEquals(manager.getImage(LOGO_DARK).content))
    }

    @Test
    fun `getImage - Answers the file a deployment configured`() {
        val manager = manager(config(logo = svg)).apply { init() }

        assertArrayEquals(svg, manager.getImage(LOGO).content)
    }

    @Test
    fun `getImage - Answers the logo on dark grounds where no dark logo is configured`() {
        val manager = manager(config(logo = svg)).apply { init() }

        assertArrayEquals(svg, manager.getImage(LOGO_DARK).content)
    }

    @Test
    fun `getImage - Answers the dark logo a deployment configured over its logo`() {
        val manager = manager(config(logo = svg, logoDark = otherSvg)).apply { init() }

        assertArrayEquals(otherSvg, manager.getImage(LOGO_DARK).content)
    }

    @Test
    fun `getImage - Answers SympAuthy's logo on light grounds where only a dark logo is configured`() {
        val manager = manager(config(logoDark = otherSvg)).apply { init() }

        assertArrayEquals(default(LOGO), manager.getImage(LOGO).content)
    }

    @Test
    fun `getImage - Answers SympAuthy's marks under a configuration that failed`() {
        val manager = manager(DisabledUIConfig(emptyList())).apply { init() }

        assertArrayEquals(default(ICON), manager.getImage(ICON).content)
        assertNull(manager.brand.displayName)
        assertEquals(DarkMode.AUTO, manager.brand.darkMode)
    }

    @Test
    fun `getImage - Gives two instances holding the same bytes the same tag`() {
        assertEquals(manager().getImage(LOGO).etag, manager().getImage(LOGO).etag)
    }

    @Test
    fun `stylesheet - Writes the configured colours`() {
        val theme = ThemeConfig(DarkMode.OFF, mapOf("primary" to "#0f766e"), emptyMap())
        val css = manager(config(theme = theme)).stylesheet.content.decodeToString()

        assertTrue("--primary: #0f766e;" in css)
    }

    @Test
    fun `fetchUntilResolved - Answers what the host answered`() = runTest {
        coEvery { client.fetchImage(uri) } returns svg
        val manager = manager()

        manager.fetchUntilResolved(LOGO, uri)

        assertArrayEquals(svg, manager.getImage(LOGO).content)
        assertFalse(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
    }

    @Test
    fun `fetchUntilResolved - Degrades on an outage and retries every quiet period until it answers`() = runTest {
        coEvery { client.fetchImage(uri) } throws ConnectException("Refused") andThen svg
        val manager = manager()
        val fallback = manager.getImage(LOGO).content

        val fetch = launch { manager.fetchUntilResolved(LOGO, uri) }
        runCurrent()

        assertTrue(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
        assertArrayEquals(fallback, manager.getImage(LOGO).content)

        advanceTimeBy(ConditionMonitor.QUIET_PERIOD.toMillis() + 1)
        fetch.join()

        assertArrayEquals(svg, manager.getImage(LOGO).content)
        coVerify(exactly = 2) { client.fetchImage(uri) }
    }

    @ParameterizedTest
    @EnumSource(names = ["REQUEST_TIMEOUT", "TOO_MANY_REQUESTS"])
    fun `fetchUntilResolved - Holds a status asking to be asked later as an outage`(status: HttpStatus) = runTest {
        coEvery { client.fetchImage(uri) } throws
            HttpClientResponseException("Later", HttpResponse.status<Any>(status)) andThen svg
        val manager = manager()

        val fetch = launch { manager.fetchUntilResolved(LOGO, uri) }
        runCurrent()

        assertTrue(conditionMonitor.isDegraded(Condition.BrandImage("logo")))

        advanceTimeBy(ConditionMonitor.QUIET_PERIOD.toMillis() + 1)
        fetch.join()

        assertArrayEquals(svg, manager.getImage(LOGO).content)

        conditionMonitor.endQuietPeriods(Instant.now().plus(ConditionMonitor.QUIET_PERIOD))

        assertFalse(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
    }

    @Test
    fun `fetchUntilResolved - Stops at a 4xx and answers as if it were not configured`() = runTest {
        coEvery { client.fetchImage(uri) } throws HttpClientResponseException("Not Found", HttpResponse.notFound<Any>())
        val manager = manager()

        manager.fetchUntilResolved(LOGO, uri)

        assertArrayEquals(default(LOGO), manager.getImage(LOGO).content)
        assertFalse(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
        coVerify(exactly = 1) { client.fetchImage(uri) }
    }

    @Test
    fun `fetchUntilResolved - Stops at an answer that is no image`() = runTest {
        coEvery { client.fetchImage(uri) } returns "<html></html>".toByteArray()
        val manager = manager()

        manager.fetchUntilResolved(LOGO, uri)

        assertArrayEquals(default(LOGO), manager.getImage(LOGO).content)
        assertFalse(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
        coVerify(exactly = 1) { client.fetchImage(uri) }
    }

    @Test
    fun `fetchUntilResolved - Stops at an answer the client would not hold`() = runTest {
        coEvery { client.fetchImage(uri) } returns null
        val manager = manager()

        manager.fetchUntilResolved(LOGO, uri)

        assertFalse(conditionMonitor.isDegraded(Condition.BrandImage("logo")))
        coVerify(exactly = 1) { client.fetchImage(uri) }
    }
}
