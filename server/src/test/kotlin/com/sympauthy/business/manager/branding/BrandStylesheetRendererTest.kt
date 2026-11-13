package com.sympauthy.business.manager.branding

import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.LIGHT_ONLY_THEME_ROLES
import com.sympauthy.config.model.THEME_ROLES
import com.sympauthy.config.model.ThemeConfig
import io.micronaut.core.io.ResourceResolver
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional

class BrandStylesheetRendererTest {

    /**
     * Tokens of its own rather than the design system's file, whose values are free to change: only the last test
     * here reads that file.
     */
    private val tokens = """
        {
          "color": {
            "tokens": [
              { "name": "primary", "value": { "light": "#111111", "dark": "#222222" } },
              { "name": "primary-hover", "value": { "light": "#333333", "dark": "#444444" } },
              { "name": "focus-ring", "value": { "light": "{primary}", "dark": "{primary}" } },
              { "name": "gold", "value": "#555555" },
              { "name": "danger", "value": { "light": "#666666", "dark": "#777777" } },
              { "name": "nav-ground", "value": { "light": "#888888", "dark": "#999999" } },
              { "name": "nav-strong", "value": "#aaaaaa" }
            ]
          },
          "spacing": { "tokens": [ { "name": "space-1.5", "value": "6px" } ] },
          "radius": { "tokens": [ { "name": "radius-md", "value": "7px" } ] },
          "size": { "tokens": [ { "name": "control-height", "value": "44px" } ] },
          "type": {
            "families": { "sans": "\"Sans\", sans-serif", "other": "serif" },
            "groups": [
              {
                "family": "sans",
                "styles": [
                  {
                    "name": "title", "fontSize": "24px", "lineHeight": "32px", "fontWeight": 800,
                    "letterSpacing": "-0.01em"
                  },
                  { "name": "code", "family": "other", "fontSize": "30px", "lineHeight": "36px", "fontWeight": 500 }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    private val ownTokens by lazy {
        mockk<ResourceResolver> {
            every { getResourceAsStream("classpath:branding/tokens.json") } answers {
                Optional.of(tokens.byteInputStream())
            }
        }
    }

    private fun render(
        theme: ThemeConfig = ThemeConfig.DEFAULT,
        resourceResolver: ResourceResolver = ownTokens
    ): String {
        val config = EnabledUIConfig(displayName = null, icon = null, logo = null, logoDark = null, theme = theme)
        return BrandStylesheetRenderer(BrandTokenResolver(resourceResolver, ObjectMapper.getDefault(), config)).render()
    }

    /**
     * The block [selector] opens, as it is written, so a value is read off the block it applies under.
     */
    private fun String.block(selector: String): String {
        val start = indexOf("$selector {\n")
        assertTrue(start >= 0, "the stylesheet has no block for $selector")
        return substring(start, indexOf("}", start))
    }

    private val lightBlock = ":root, [data-theme=\"light\"]"
    private val systemDarkBlock = ":root:not([data-theme=\"light\"])"
    private val pinnedDarkBlock = "\n[data-theme=\"dark\"]"

    @Test
    fun `render - With nothing configured writes every token`() {
        val css = render()

        val light = css.block(lightBlock)
        assertTrue("--primary: #111111;" in light)
        assertTrue("--gold: #555555;" in light)
        assertTrue("--danger: #666666;" in light)
        assertTrue("--nav-ground: #888888;" in light)
        assertTrue("--primary: #222222;" in css.block(systemDarkBlock))
        assertTrue("--danger: #777777;" in css.block(pinnedDarkBlock))
        assertTrue("--radius-md: 7px;" in css)
        assertTrue("--control-height: 44px;" in css)
    }

    @Test
    fun `render - Writes an alias as the variable it names`() {
        val css = render()

        assertTrue("--focus-ring: var(--primary);" in css.block(lightBlock))
    }

    @Test
    fun `render - Escapes a dot in a token's name`() {
        assertTrue("--space-1\\.5: 6px;" in render())
    }

    @Test
    fun `render - Writes the fonts and the type styles`() {
        val css = render()

        assertTrue("--font-sans: 'Plus Jakarta Sans Variable', \"Sans\", sans-serif;" in css)
        assertTrue("--font-other: serif;" in css)
        assertTrue(
            ".title { font-family: var(--font-sans); font-size: 24px; line-height: 32px; font-weight: 800; " +
                "letter-spacing: -0.01em; }" in css
        )
        assertTrue(".code { font-family: var(--font-other);" in css)
    }

    @Test
    fun `render - Writes a configured colour over its default and leaves every other token`() {
        val configured = render(ThemeConfig(DarkMode.AUTO, mapOf("primary" to "#0f766e"), emptyMap()))
        val default = render()

        assertEquals(default.replace("--primary: #111111;", "--primary: #0f766e;"), configured)
    }

    @Test
    fun `render - Writes the accent as the stylesheet's gold`() {
        val css = render(ThemeConfig(DarkMode.AUTO, mapOf("accent" to "#abcdef"), emptyMap()))

        assertTrue("--gold: #abcdef;" in css.block(lightBlock))
    }

    @Test
    fun `render - Writes a configured nav-strong over its default, in the light block alone`() {
        val css = render(ThemeConfig(DarkMode.AUTO, mapOf("nav-strong" to "#abcdef"), emptyMap()))

        assertTrue("--nav-strong: #abcdef;" in css.block(lightBlock))
        assertFalse("--nav-strong: #aaaaaa;" in css)
        assertFalse("--nav-strong" in css.block(systemDarkBlock))
        assertFalse("--nav-strong" in css.block(pinnedDarkBlock))
    }

    @Test
    fun `render - Writes a dark colour under the device's preference and under a page pinning dark`() {
        val css = render(ThemeConfig(DarkMode.AUTO, emptyMap(), mapOf("primary" to "#84c2ba")))

        assertTrue("--primary: #84c2ba;" in css.block(systemDarkBlock))
        assertTrue("--primary: #84c2ba;" in css.block(pinnedDarkBlock))
        assertTrue("--primary: #111111;" in css.block(lightBlock))
        assertTrue(css.contains("@media (prefers-color-scheme: dark) {\n  $systemDarkBlock {"))
    }

    @Test
    fun `render - Keeps the sidebar's roles and the single-valued tokens out of the dark blocks`() {
        val css = render()

        listOf(systemDarkBlock, pinnedDarkBlock).forEach { block ->
            assertFalse("--nav-" in css.block(block))
            assertFalse("--gold" in css.block(block))
        }
    }

    @Test
    fun `render - Leaves the dark roles out under dark-mode off`() {
        val css = render(ThemeConfig(DarkMode.OFF, emptyMap(), emptyMap()))

        assertFalse("prefers-color-scheme" in css)
        assertFalse("data-theme=\"dark\"" in css)
        assertFalse("--primary: #222222;" in css)
    }

    @Test
    fun `render - Every role a deployment may colour names a token of the design system this server ships`() {
        val css = render(resourceResolver = ResourceResolver())
        val light = css.block(lightBlock)
        val dark = css.block(systemDarkBlock)

        val missing = (THEME_ROLES + LIGHT_ONLY_THEME_ROLES).filterNot {
            val token = if (it == "accent") "gold" else it
            "--$token:" in light
        } + THEME_ROLES.filterNot { "--$it:" in dark }

        assertEquals(emptyList<String>(), missing)
    }
}
