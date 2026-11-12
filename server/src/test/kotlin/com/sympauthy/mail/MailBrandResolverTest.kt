package com.sympauthy.mail

import com.sympauthy.business.manager.branding.BrandTokenResolver
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.ThemeConfig
import io.micronaut.core.io.ResourceResolver
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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

    /**
     * No root URL is configured, so the brand carries no logo and the brand manager is never asked for one.
     */
    private fun resolve(theme: ThemeConfig = ThemeConfig.DEFAULT): Map<String, Any> {
        val resourceResolver = mockk<ResourceResolver> {
            every { getResourceAsStream("classpath:branding/tokens.json") } answers {
                Optional.of(tokens.byteInputStream())
            }
        }
        val config = EnabledUIConfig(displayName = null, icon = null, logo = null, logoDark = null, theme = theme)
        val tokenResolver = BrandTokenResolver(resourceResolver, ObjectMapper.getDefault(), config)
        return MailBrandResolver(tokenResolver, mockk(), config, DisabledUrlsConfig(emptyList())).resolve()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.palette(theme: String) = getValue(theme) as Map<String, String>

    @Test
    fun `resolve - Resolves an alias to the colour configured for the token it names`() {
        val brand = resolve(ThemeConfig(DarkMode.AUTO, mapOf("primary" to "#0f766e"), mapOf("primary" to "#84c2ba")))

        assertEquals("#0f766e", brand.palette("light")["focus-ring"])
        assertEquals("#84c2ba", brand.palette("dark")["focus-ring"])
    }

    @Test
    fun `resolve - Resolves a dark alias to the light colour of a token the dark theme does not carry`() {
        val brand = resolve()

        assertEquals("#555555", brand.palette("dark")["keyhole"])
        assertEquals(null, brand.palette("dark")["gold"])
    }
}
