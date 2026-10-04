package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.properties.ClaimAclProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration

class ClaimAclParserTest {

    private val parser = ClaimAclParser(ConfigParser())

    private fun aclProperties(
        consentScope: String? = null,
        readableByPerson: String? = null,
        collectedInFlow: String? = null,
        readableByClient: String? = null,
        writableByClient: String? = null,
        readableWithClientScopes: List<String>? = null,
        writableWithClientScopes: List<String>? = null,
        writableByPerson: String? = null,
        writeMaxAuthenticationAge: String? = null
    ): ClaimAclProperties = object : ClaimAclProperties {
        override val consentScope = consentScope
        override val readableByPersonWhenConsented = readableByPerson
        override val collectedInFlowWhenConsented = collectedInFlow
        override val writableByPersonWhenConsented = writableByPerson
        override val readableByClientWhenConsented = readableByClient
        override val writableByClientWhenConsented = writableByClient
        override val readableWithClientScopesUnconditionally = readableWithClientScopes
        override val writableWithClientScopesUnconditionally = writableWithClientScopes
        override val writeMaxAuthenticationAge = writeMaxAuthenticationAge
    }

    private fun template(
        consentScope: String? = null,
        readableByPerson: Boolean? = null,
        collectedInFlow: Boolean? = null,
        readableByClient: Boolean? = null,
        writableByClient: Boolean? = null,
        readableWithClientScopes: List<String>? = null,
        writableWithClientScopes: List<String>? = null,
        writableByPerson: Boolean? = null,
        writeMaxAuthenticationAge: Duration? = null
    ) = ClaimTemplate(
        id = "test",
        enabled = null,
        required = null,
        group = null,
        kind = null,
        audienceId = null,
        allowedValues = null,
        publishedIn = null,
        publishedInWhenRequested = null,
        acl = ClaimTemplateAcl(
            consentScope = consentScope,
            readableByPersonWhenConsented = readableByPerson,
            collectedInFlowWhenConsented = collectedInFlow,
            writableByPersonWhenConsented = writableByPerson,
            readableByClientWhenConsented = readableByClient,
            writableByClientWhenConsented = writableByClient,
            readableWithClientScopesUnconditionally = readableWithClientScopes,
            writableWithClientScopesUnconditionally = writableWithClientScopes,
            writeMaxAuthenticationAge = writeMaxAuthenticationAge
        )
    )

    @Test
    fun `parseTemplateAcl - Return all nulls when there is no acl`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseTemplateAcl(ctx, null, "templates.claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = null,
                readableByPerson = null,
                collectedInFlow = null,
                writableByPerson = null,
                readableByClient = null,
                writableByClient = null,
                readableWithClientScopes = null,
                writableWithClientScopes = null,
                writeMaxAuthenticationAge = null
            ),
            parsed
        )
    }

    @Test
    fun `parseTemplateAcl - Read every field from the properties`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(
            consentScope = "profile",
            readableByPerson = "true",
            collectedInFlow = "false",
            readableByClient = "true",
            writableByClient = "false",
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val parsed = parser.parseTemplateAcl(ctx, acl, "templates.claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = "profile",
                readableByPerson = true,
                collectedInFlow = false,
                writableByPerson = null,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write"),
                writeMaxAuthenticationAge = null
            ),
            parsed
        )
    }

    @Test
    fun `parseTemplateAcl - Report every boolean that did not parse`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByPerson = "bad", writableByClient = "worse")

        parser.parseTemplateAcl(ctx, acl, "templates.claims.test")

        assertEquals(listOf("config.invalid_boolean", "config.invalid_boolean"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf(
                "templates.claims.test.acl.readable-by-person-when-consented",
                "templates.claims.test.acl.writable-by-client-when-consented"
            ),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `parseAcl - Read every field from the properties`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(
            consentScope = "profile",
            readableByPerson = "true",
            collectedInFlow = "false",
            readableByClient = "true",
            writableByClient = "false",
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val parsed = parser.parseAcl(ctx, acl, null, "claims.test", null)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = "profile",
                readableByPerson = true,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write"),
                writeMaxAuthenticationAge = null
            ),
            parsed
        )
    }

    @Test
    fun `parseAcl - Fall back to the template when the properties set nothing`() {
        val ctx = ConfigParsingContext()
        val template = template(
            consentScope = "email",
            readableByPerson = true,
            collectedInFlow = false,
            readableByClient = true,
            writableByClient = false,
            readableWithClientScopes = listOf("users:claims:read")
        )

        val parsed = parser.parseAcl(ctx, null, template, "claims.test", null)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = "email",
                readableByPerson = true,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = emptyList(),
                writeMaxAuthenticationAge = null
            ),
            parsed
        )
    }

    @Test
    fun `parseAcl - Fall back to the default consent scope when neither the properties nor the template name one`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(ctx, null, null, "claims.test", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals("profile", parsed.consentScope)
    }

    @Test
    fun `parseAcl - Default to false and an empty list when nothing is set`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(ctx, null, null, "claims.test", null)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = null,
                readableByPerson = false,
                collectedInFlow = false,
                writableByPerson = false,
                readableByClient = false,
                writableByClient = false,
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList(),
                writeMaxAuthenticationAge = null
            ),
            parsed
        )
    }

    @Test
    fun `parseAcl - Let the properties override the template`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(
            ctx,
            aclProperties(readableByPerson = "false"),
            template(readableByPerson = true),
            "claims.test",
            null
        )

        assertFalse(ctx.hasErrors)
        assertEquals(false, parsed.readableByPerson)
    }

    @Test
    fun `parseAcl - Report every boolean that did not parse`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByPerson = "not_a_boolean", collectedInFlow = "also_bad")

        parser.parseAcl(ctx, acl, null, "claims.test", null)

        assertEquals(listOf("config.invalid_boolean", "config.invalid_boolean"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf(
                "claims.test.acl.readable-by-person-when-consented",
                "claims.test.acl.collected-in-flow-when-consented"
            ),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `parseAcl - Read the maximum authentication age as a duration`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(
            ctx,
            aclProperties(writeMaxAuthenticationAge = "5m"),
            null,
            "claims.test",
            null
        )

        assertFalse(ctx.hasErrors)
        assertEquals(Duration.ofMinutes(5), parsed.writeMaxAuthenticationAge)
    }

    @Test
    fun `parseAcl - Fall back to the template maximum authentication age`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(
            ctx,
            null,
            template(writeMaxAuthenticationAge = Duration.ofMinutes(10)),
            "claims.test",
            null
        )

        assertFalse(ctx.hasErrors)
        assertEquals(Duration.ofMinutes(10), parsed.writeMaxAuthenticationAge)
    }

    @Test
    fun `parseAcl - Report a maximum authentication age that is not a duration`() {
        val ctx = ConfigParsingContext()

        parser.parseAcl(ctx, aclProperties(writeMaxAuthenticationAge = "soon"), null, "claims.test", null)

        assertEquals(listOf("config.invalid_duration"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.test.acl.write-max-authentication-age"), ctx.errors.map { it.key })
    }
}
