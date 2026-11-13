package com.sympauthy.mail

import com.sympauthy.business.manager.branding.BrandManager
import com.sympauthy.business.manager.branding.BrandTokenResolver
import com.sympauthy.business.model.branding.Brand
import com.sympauthy.business.model.branding.BrandAsset
import com.sympauthy.business.model.branding.DarkMode
import com.sympauthy.business.model.branding.pngOf
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.ThemeConfig
import freemarker.template.Configuration
import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.micronaut.core.io.ResourceResolver
import io.micronaut.serde.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * The mail templates rendered as the server renders them, with the mail bundle and the design system's tokens it
 * ships: a key the bundle lacks or a token the kit reads and the tokens lack fails the rendering, as it would fail
 * every mail sent.
 */
class MailKitTest {

    private val freemarker = Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).apply {
        setClassForTemplateLoading(MailKitTest::class.java, "/views")
        defaultEncoding = "UTF-8"
    }

    private val root = "https://auth.example.com"

    private class Rendered(val html: String, val brand: Map<String, Any>)

    private fun render(
        template: String,
        parameters: Map<String, String>,
        darkMode: DarkMode = DarkMode.AUTO,
        displayName: String? = null,
        withLogo: Boolean = false
    ): Rendered {
        val config = EnabledUIConfig(
            displayName = displayName,
            icon = null,
            logo = null,
            logoDark = null,
            theme = ThemeConfig(darkMode, emptyMap(), emptyMap())
        )
        val brandManager = mockk<BrandManager> {
            every { brand } returns Brand(displayName, darkMode)
            if (withLogo) every { getMailImage(any()) } returns BrandAsset(pngOf(200, 100), "image/png")
        }
        val urls = if (withLogo) EnabledUrlsConfig(URI.create(root)) else DisabledUrlsConfig(emptyList())
        val tokenResolver = BrandTokenResolver(ResourceResolver(), ObjectMapper.getDefault(), config)
        val factory = TemplatedMailBuilderFactory(
            defaultFrom = null,
            messageSource = ResourceBundleMessageSource("mail_messages"),
            mailBrandResolver = MailBrandResolver(tokenResolver, brandManager, urls)
        )
        val model = factory.builder(
            template = "mails/$template",
            locale = Locale.US,
            parameters = parameters,
            expirationDate = LocalDateTime.now().plusMinutes(15)
        ).model
        val html = StringWriter().also { freemarker.getTemplate("mails/$template.ftl").process(model, it) }
        @Suppress("UNCHECKED_CAST")
        return Rendered(html.toString(), model.getValue(TemplatedMailBuilder.BRAND_MODEL_KEY) as Map<String, Any>)
    }

    private val code = mapOf("code" to "123456")
    private val resetLink = mapOf("resetUrl" to "$root/reset?token=abc")

    private val everyMail = listOf("validation_code" to code, "reset_password" to code, "reset_password" to resetLink)

    @Test
    fun `Every mail renders under every brand a deployment may configure`() {
        everyMail.forEach { (template, parameters) ->
            DarkMode.entries.forEach { darkMode ->
                listOf(null, "Acme").forEach { displayName ->
                    listOf(false, true).forEach { withLogo ->
                        val html = render(template, parameters, darkMode, displayName, withLogo).html
                        assertTrue(html.startsWith("<!DOCTYPE html>"), "$template under $darkMode")
                        if (withLogo) assertTrue("$root/branding/logo.png?v=" in html, "$template shows its logo")
                    }
                }
            }
        }
    }

    @Test
    fun `A mail carries its code in groups of three, copied as one string`() {
        val html = render("validation_code", code).html

        assertTrue(">123<span style=\"padding-left:16px;\">456</span><" in html)
    }

    @Test
    fun `A mail carries the link it was given, on its button and written out`() {
        val html = render("reset_password", resetLink).html

        assertEquals(3, html.split("href=\"$root/reset?token=abc\"").size - 1)
    }

    @Test
    fun `A mail escapes the name a deployment configured`() {
        val html = render("validation_code", code, displayName = "Acme & <Co>").html

        assertTrue("Acme &amp; &lt;Co&gt;" in html)
        assertTrue("<Co>" !in html)
    }

    @Test
    fun `Every colour a mail paints is repainted where the device is dark`() {
        everyMail.forEach { (template, parameters) ->
            val rendered = render(template, parameters, withLogo = true)
            @Suppress("UNCHECKED_CAST")
            val dark = rendered.brand.getValue("dark") as Map<String, String>

            val painted = Regex("""class="([^"]*)"""").findAll(rendered.html)
                .flatMap { it.groupValues[1].split(" ") }
                .mapNotNull { Regex("""^sa-(bg|fg|bd)-(.+)$""").matchEntire(it)?.destructured }
                .toSet()

            painted.filter { (_, token) -> token in dark }.forEach { (kind, token) ->
                assertTrue(".sa-$kind-$token {" in rendered.html, "$template paints sa-$kind-$token, never repainted")
            }
        }
    }
}
