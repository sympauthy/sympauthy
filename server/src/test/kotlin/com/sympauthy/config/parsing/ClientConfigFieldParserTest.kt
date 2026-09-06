package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.AccessReviewOnFailure
import com.sympauthy.business.model.client.AccessReviewTrigger
import com.sympauthy.business.model.client.AccessReviewWebhook
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.properties.ClientAccessReviewWebhookProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * The access-review webhook a client configures, and what it takes from the template it was left out
 * of. The authorization webhook beside it is parsed the same way, through the same two helpers.
 */
class ClientConfigFieldParserTest {

    private val parser = ClientConfigFieldParser(ConfigParser(), ConfigTemplateResolver())

    private val configKey = "clients.my-app.webhooks.access-review"

    @Test
    fun `parseAccessReviewWebhook - Answer nothing where neither the client nor its template names one`() {
        val ctx = ConfigParsingContext()

        assertNull(parser.parseAccessReviewWebhook(ctx, configKey, null, null))
    }

    @Test
    fun `parseAccessReviewWebhook - Read what the client named`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAccessReviewWebhook(
            ctx, configKey,
            properties(
                url = "https://my-app.example/security/access-review",
                secret = "a-shared-secret",
                on = "every-validation",
                onFailure = "allow"
            ),
            null
        )

        assertEquals(URI.create("https://my-app.example/security/access-review"), parsed?.url)
        assertEquals("a-shared-secret", parsed?.secret)
        assertEquals(AccessReviewTrigger.EVERY_VALIDATION, parsed?.on)
        assertEquals(AccessReviewOnFailure.ALLOW, parsed?.onFailure)
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parseAccessReviewWebhook - Fire on a context carrying no allow, and refuse, unless told otherwise`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAccessReviewWebhook(
            ctx, configKey,
            properties(url = "https://my-app.example/review", secret = "secret"),
            null
        )

        assertEquals(AccessReviewTrigger.NEW_CONTEXT, parsed?.on)
        assertEquals(AccessReviewOnFailure.DENY, parsed?.onFailure)
    }

    @Test
    fun `parseAccessReviewWebhook - Take a field the client left out from its template`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAccessReviewWebhook(
            ctx, configKey,
            properties(url = "https://my-app.example/review"),
            template()
        )

        assertEquals(URI.create("https://my-app.example/review"), parsed?.url)
        assertEquals("template-secret", parsed?.secret)
        assertEquals(AccessReviewTrigger.EVERY_VALIDATION, parsed?.on)
        assertEquals(AccessReviewOnFailure.ALLOW, parsed?.onFailure)
    }

    @Test
    fun `parseAccessReviewWebhook - Report a url that is not absolute`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAccessReviewWebhook(
            ctx, configKey,
            properties(url = "/security/access-review", secret = "secret"),
            null
        )

        assertNull(parsed)
        assertEquals(
            listOf("$configKey.url" to "config.invalid_url"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parseAccessReviewWebhook - Report a trigger naming nothing it can be called on`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAccessReviewWebhook(
            ctx, configKey,
            properties(url = "https://my-app.example/review", secret = "secret", on = "sometimes"),
            null
        )

        assertNull(parsed)
        assertEquals(
            listOf("$configKey.on" to "config.invalid_enum_value"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    private fun properties(
        url: String? = null,
        secret: String? = null,
        on: String? = null,
        onFailure: String? = null
    ) = object : ClientAccessReviewWebhookProperties {
        override val url = url
        override val secret = secret
        override val on = on
        override val onFailure = onFailure
    }

    private fun template() = AccessReviewWebhook(
        url = URI.create("https://template.example/review"),
        secret = "template-secret",
        on = AccessReviewTrigger.EVERY_VALIDATION,
        onFailure = AccessReviewOnFailure.ALLOW
    )
}
