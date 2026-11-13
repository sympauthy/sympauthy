package com.sympauthy.mail

import com.sympauthy.business.manager.branding.BrandManager
import com.sympauthy.business.manager.branding.BrandTokenResolver
import com.sympauthy.business.model.branding.Brand
import com.sympauthy.business.model.branding.BrandAsset
import com.sympauthy.business.model.branding.BrandImageName.LOGO
import com.sympauthy.business.model.branding.BrandImageName.LOGO_DARK
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.business.model.branding.pngOf
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.ThemeConfig
import com.sympauthy.config.model.UrlsConfig
import io.micronaut.core.io.ResourceResolver
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.Optional

class MailBrandResolverTest {

    private val tokens = """
        {
          "color": {
            "tokens": [
              { "name": "primary", "value": { "light": "#111111", "dark": "#222222" } },
              { "name": "focus-ring", "value": { "light": "{primary}", "dark": "{primary}" } },
              { "name": "gold", "value": "#555555" },
              { "name": "keyhole", "value": { "light": "{gold}", "dark": "{gold}" } }
            ]
          }
        }
    """.trimIndent()

    private val brandManager = mockk<BrandManager>()

    private fun resolver(
        theme: ThemeConfig = ThemeConfig.DEFAULT,
        displayName: String? = null,
        urls: UrlsConfig = DisabledUrlsConfig(emptyList())
    ): MailBrandResolver {
        val resourceResolver = mockk<ResourceResolver> {
            every { getResourceAsStream("classpath:branding/tokens.json") } answers {
                Optional.of(tokens.byteInputStream())
            }
        }
        val config = EnabledUIConfig(displayName = null, icon = null, logo = null, logoDark = null, theme = theme)
        every { brandManager.brand } returns Brand(displayName, theme.darkMode)
        val tokenResolver = BrandTokenResolver(resourceResolver, ObjectMapper.getDefault(), config)
        return MailBrandResolver(tokenResolver, brandManager, urls)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.palette(theme: String) = getValue(theme) as Map<String, String>

    @Test
    fun `resolve - Resolves an alias to the colour configured for the token it names`() {
        val theme = ThemeConfig(DarkMode.AUTO, mapOf("primary" to "#0f766e"), mapOf("primary" to "#84c2ba"))
        val brand = resolver(theme).resolve()

        assertEquals("#0f766e", brand.palette("light")["focus-ring"])
        assertEquals("#84c2ba", brand.palette("dark")["focus-ring"])
    }

    @Test
    fun `resolve - Resolves a dark alias to the light colour of a token the dark theme does not carry`() {
        val brand = resolver().resolve()

        assertEquals("#555555", brand.palette("dark")["keyhole"])
        assertNull(brand.palette("dark")["gold"])
    }

    @Test
    fun `resolve - Signs the mail with the configured display name`() {
        val brand = resolver(displayName = "Acme").resolve()

        assertEquals("Acme", brand["displayName"])
        assertEquals(false, brand["isSympAuthy"])
    }

    @Test
    fun `resolve - Signs the mail as SympAuthy where no display name is configured`() {
        val brand = resolver().resolve()

        assertEquals("SympAuthy", brand["displayName"])
        assertEquals(true, brand["isSympAuthy"])
    }

    @Test
    fun `resolve - Links the PNG logo under the root URL, versioned by its content, at its width 40px tall`() {
        val png = BrandAsset(pngOf(200, 100), "image/png")
        every { brandManager.getMailImage(LOGO) } returns png
        every { brandManager.getMailImage(LOGO_DARK) } returns null

        val brand = resolver(urls = EnabledUrlsConfig(URI.create("https://auth.example.com"))).resolve()

        val version = png.etag.trim('"').take(12)
        assertEquals("https://auth.example.com/branding/logo.png?v=$version", brand["logoUrl"])
        assertEquals(80, brand["logoWidth"])
        assertFalse("logoDarkUrl" in brand)
    }

    @Test
    fun `resolve - Shows no logo where the mail has none to show`() {
        every { brandManager.getMailImage(any()) } returns null

        val brand = resolver(urls = EnabledUrlsConfig(URI.create("https://auth.example.com"))).resolve()

        assertFalse("logoUrl" in brand)
        assertTrue("displayName" in brand)
    }

    @Test
    fun `resolve - Shows no logo where no root URL is configured`() {
        assertFalse("logoUrl" in resolver().resolve())
    }

    @Test
    fun `resolve - Shows no logo where the root URL is local`() {
        val brand = resolver(urls = EnabledUrlsConfig(URI.create("http://localhost:8080"))).resolve()

        assertFalse("logoUrl" in brand)
    }
}
