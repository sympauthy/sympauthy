package com.sympauthy.config.factory

import com.sympauthy.business.model.branding.BrandImage
import com.sympauthy.business.model.branding.BrandImageType
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.WrittenConfigurationKeys
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledUIConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.LocalBrandImage
import com.sympauthy.config.model.RemoteBrandImage
import com.sympauthy.config.model.UIConfig
import com.sympauthy.config.parsing.UIConfigParser
import com.sympauthy.config.properties.ThemeConfigurationProperties
import com.sympauthy.config.properties.UIConfigurationProperties
import com.sympauthy.config.validation.UIConfigValidator
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class UIConfigFactoryTest {

    @TempDir
    lateinit var directory: Path

    private fun provide(
        displayName: String? = null,
        icon: String? = null,
        logo: String? = null,
        logoDark: String? = null,
        darkMode: String? = null,
        light: Map<String, String?>? = null,
        dark: Map<String, String?>? = null,
        writtenKeys: Set<String> = buildSet {
            light?.keys?.forEach { add("ui.theme.light.$it") }
            dark?.keys?.forEach { add("ui.theme.dark.$it") }
        }
    ): UIConfig {
        val written = mockk<WrittenConfigurationKeys> { every { under("ui.theme.") } returns writtenKeys }
        val properties = mockk<UIConfigurationProperties> {
            every { this@mockk.displayName } returns displayName
            every { this@mockk.icon } returns icon
            every { this@mockk.logo } returns logo
            every { this@mockk.logoDark } returns logoDark
        }
        val themeProperties = mockk<ThemeConfigurationProperties> {
            every { this@mockk.darkMode } returns darkMode
            every { this@mockk.light } returns light
            every { this@mockk.dark } returns dark
        }
        return UIConfigFactory(UIConfigParser(ConfigParser()), UIConfigValidator(), written)
            .provideUIConfig(properties, themeProperties)
    }

    private fun UIConfig.enabled() = assertInstanceOf(EnabledUIConfig::class.java, this)

    private fun UIConfig.errors(): List<Pair<String, String>> =
        assertInstanceOf(DisabledUIConfig::class.java, this).configurationErrors!!
            .filterIsInstance<ConfigurationException>()
            .map { it.key to it.messageId }

    private fun file(name: String, content: ByteArray): String =
        Files.write(directory.resolve(name), content).toString()

    @Test
    fun `provideUIConfig - With nothing configured has no name, no image and the default theme`() {
        val config = provide().enabled()

        assertNull(config.displayName)
        assertNull(config.icon)
        assertNull(config.logo)
        assertNull(config.logoDark)
        assertEquals(DarkMode.AUTO, config.theme.darkMode)
        assertEquals(emptyMap<String, String>(), config.theme.light)
        assertEquals(emptyMap<String, String>(), config.theme.dark)
    }

    @Test
    fun `provideUIConfig - Refuses a blank display name`() {
        assertEquals(listOf("ui.display-name" to "config.empty"), provide(displayName = " ").errors())
    }

    @Test
    fun `provideUIConfig - Keeps the colours by role`() {
        val light = mapOf(
            "primary" to "#0f766e", "ink-50" to "#111111", "accent" to "#222222", "nav-ground" to "#ABCDEF"
        )
        val config = provide(
            light = light,
            dark = mapOf("primary" to "#84c2ba")
        ).enabled()

        assertEquals(light, config.theme.light)
        assertEquals(mapOf("primary" to "#84c2ba"), config.theme.dark)
    }

    @Test
    fun `provideUIConfig - Reads the word off, and the false YAML reads it as`() {
        assertEquals(DarkMode.OFF, provide(darkMode = "off").enabled().theme.darkMode)
        assertEquals(DarkMode.OFF, provide(darkMode = "false").enabled().theme.darkMode)
        assertEquals(DarkMode.AUTO, provide(darkMode = "auto").enabled().theme.darkMode)
    }

    @Test
    fun `provideUIConfig - Refuses a dark mode other than auto or off`() {
        assertEquals(
            listOf("ui.theme.dark-mode" to "config.invalid_enum_value"),
            provide(darkMode = "maybe").errors()
        )
    }

    @Test
    fun `provideUIConfig - Refuses the true YAML reads on as, without naming a word nobody wrote`() {
        assertEquals(
            listOf("ui.theme.dark-mode" to "config.ui.theme.dark_mode_on"),
            provide(darkMode = "true").errors()
        )
    }

    @Test
    fun `provideUIConfig - Refuses each colour that is not rrggbb`() {
        val errors = provide(
            light = mapOf("primary" to "red", "ground" to "#fff"),
            dark = mapOf("surface" to "#12345g")
        ).errors()

        assertEquals(
            setOf(
                "ui.theme.light.primary" to "config.ui.theme.invalid_color",
                "ui.theme.light.ground" to "config.ui.theme.invalid_color",
                "ui.theme.dark.surface" to "config.ui.theme.invalid_color"
            ),
            errors.toSet()
        )
    }

    @Test
    fun `provideUIConfig - Refuses a colour YAML read as a comment`() {
        assertEquals(
            listOf("ui.theme.light.primary" to "config.ui.theme.invalid_color"),
            provide(writtenKeys = setOf("ui.theme.light.primary")).errors()
        )
    }

    @Test
    fun `provideUIConfig - Refuses a role under dark that only light has`() {
        listOf("accent", "nav-ground").forEach { role ->
            assertEquals(
                listOf("ui.theme.dark.$role" to "config.ui.theme.dark.light_only_role"),
                provide(dark = mapOf(role to "#000000")).errors()
            )
        }
    }

    @Test
    fun `provideUIConfig - Refuses a role that does not exist`() {
        assertEquals(
            listOf("ui.theme.light.danger" to "config.ui.theme.unknown_role"),
            provide(light = mapOf("danger" to "#000000")).errors()
        )
    }

    @Test
    fun `provideUIConfig - Ignores a map entry nobody wrote, which is how an environment variable reaches it`() {
        val config = provide(
            light = mapOf("primary-hover" to "#123456", "primary.hover" to "#123456"),
            writtenKeys = emptySet()
        ).enabled()

        assertEquals(mapOf("primary-hover" to "#123456"), config.theme.light)
    }

    @Test
    fun `provideUIConfig - Refuses a dark theme alongside dark-mode off, under the key it was written at`() {
        assertEquals(
            listOf("ui.theme.dark" to "config.ui.theme.dark.dark_mode_off"),
            provide(darkMode = "off", dark = mapOf("primary" to "#000000")).errors()
        )
    }

    @Test
    fun `provideUIConfig - Refuses an empty dark section alongside dark-mode off too`() {
        assertEquals(
            listOf("ui.theme.dark" to "config.ui.theme.dark.dark_mode_off"),
            provide(darkMode = "off", writtenKeys = setOf("ui.theme.dark")).errors()
        )
    }

    @Test
    fun `provideUIConfig - Takes a value starting with http or https as a URL`() {
        val config = provide(icon = "http://assets.internal/icon.svg", logo = "https://cdn.example/logo.svg").enabled()

        assertEquals(RemoteBrandImage(URI.create("http://assets.internal/icon.svg")), config.icon)
        assertEquals(RemoteBrandImage(URI.create("https://cdn.example/logo.svg")), config.logo)
    }

    @Test
    fun `provideUIConfig - Keeps a URL as it was written, since a host may sign its query`() {
        val written = "https://cdn.example/logo.svg?sig=abc+def/ghi="

        val config = provide(logo = written).enabled()

        assertEquals(written, (config.logo as RemoteBrandImage).uri.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://cdn.example/my logo.svg", "https://cdn.example/a%zz.svg", "https://"])
    fun `provideUIConfig - Refuses a malformed URL rather than stop the server`(url: String) {
        assertEquals(listOf("ui.logo" to "config.invalid_url"), provide(logo = url).errors())
    }

    @Test
    fun `provideUIConfig - Reads a local SVG and a local PNG`() {
        val svg = "<svg/>".toByteArray()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        val config = provide(logo = file("logo.svg", svg), logoDark = file("logo.png", png)).enabled()

        assertEquals(BrandImageType.SVG, (config.logo as LocalBrandImage).image.type)
        assertEquals(BrandImageType.PNG, (config.logoDark as LocalBrandImage).image.type)
    }

    @Test
    fun `provideUIConfig - Refuses a file that does not exist`() {
        assertEquals(
            listOf("ui.logo" to "config.ui.image.not_found"),
            provide(logo = directory.resolve("missing.svg").toString()).errors()
        )
    }

    @Test
    fun `provideUIConfig - Reads another scheme as a path, and refuses it as a file that does not exist`() {
        val errors = provide(icon = "ftp://cdn.example/i.svg").errors()

        assertEquals(listOf("ui.icon" to "config.ui.image.not_found"), errors)
    }

    @Test
    fun `provideUIConfig - Refuses a path that cannot be read as a file`() {
        assertEquals(listOf("ui.logo" to "config.ui.image.unreadable"), provide(logo = directory.toString()).errors())
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `provideUIConfig - Refuses a device without reading it`() {
        assertEquals(listOf("ui.logo" to "config.ui.image.unreadable"), provide(logo = "/dev/zero").errors())
    }

    @Test
    fun `provideUIConfig - Refuses a file over the limit`() {
        val path = file("large.svg", "<svg>".toByteArray() + ByteArray(BrandImage.MAX_SIZE))

        assertEquals(listOf("ui.logo" to "config.ui.image.too_large"), provide(logo = path).errors())
    }

    @Test
    fun `provideUIConfig - Refuses a file of another type`() {
        val jpeg = file("logo.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        assertEquals(listOf("ui.logo" to "config.ui.image.unsupported_type"), provide(logo = jpeg).errors())
    }

    @Test
    fun `provideUIConfig - Reports every mistake together`() {
        val errors = provide(
            logo = "/no/such/file",
            darkMode = "maybe",
            light = mapOf("primary" to "red")
        ).errors()

        assertEquals(3, errors.size)
    }
}
