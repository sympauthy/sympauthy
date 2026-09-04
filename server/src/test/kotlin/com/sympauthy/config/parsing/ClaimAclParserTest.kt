package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.properties.ClaimAclProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ClaimAclParserTest {

    private val parser = ClaimAclParser(ConfigParser())

    private fun aclProperties(
        consentScope: String? = null,
        readableByUser: String? = null,
        writableByUser: String? = null,
        readableByClient: String? = null,
        writableByClient: String? = null,
        readableWithClientScopes: List<String>? = null,
        writableWithClientScopes: List<String>? = null
    ): ClaimAclProperties = object : ClaimAclProperties {
        override val consentScope = consentScope
        override val readableByUserWhenConsented = readableByUser
        override val writableByUserWhenConsented = writableByUser
        override val readableByClientWhenConsented = readableByClient
        override val writableByClientWhenConsented = writableByClient
        override val readableWithClientScopesUnconditionally = readableWithClientScopes
        override val writableWithClientScopesUnconditionally = writableWithClientScopes
    }

    private fun template(
        consentScope: String? = null,
        readableByUser: Boolean? = null,
        writableByUser: Boolean? = null,
        readableByClient: Boolean? = null,
        writableByClient: Boolean? = null,
        readableWithClientScopes: List<String>? = null,
        writableWithClientScopes: List<String>? = null
    ) = ClaimTemplate(
        id = "test",
        enabled = null,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = null,
        acl = ClaimTemplateAcl(
            consentScope = consentScope,
            readableByUserWhenConsented = readableByUser,
            writableByUserWhenConsented = writableByUser,
            readableByClientWhenConsented = readableByClient,
            writableByClientWhenConsented = writableByClient,
            readableWithClientScopesUnconditionally = readableWithClientScopes,
            writableWithClientScopesUnconditionally = writableWithClientScopes
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
                readableByUser = null,
                writableByUser = null,
                readableByClient = null,
                writableByClient = null,
                readableWithClientScopes = null,
                writableWithClientScopes = null
            ),
            parsed
        )
    }

    @Test
    fun `parseTemplateAcl - Read every field from the properties`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(
            consentScope = "profile",
            readableByUser = "true",
            writableByUser = "false",
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
                readableByUser = true,
                writableByUser = false,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write")
            ),
            parsed
        )
    }

    @Test
    fun `parseTemplateAcl - Report every boolean that did not parse`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByUser = "bad", writableByClient = "worse")

        parser.parseTemplateAcl(ctx, acl, "templates.claims.test")

        assertEquals(listOf("config.invalid_boolean", "config.invalid_boolean"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf(
                "templates.claims.test.acl.readable-by-user-when-consented",
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
            readableByUser = "true",
            writableByUser = "false",
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
                readableByUser = true,
                writableByUser = false,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = listOf("users:claims:write")
            ),
            parsed
        )
    }

    @Test
    fun `parseAcl - Fall back to the template when the properties set nothing`() {
        val ctx = ConfigParsingContext()
        val template = template(
            consentScope = "email",
            readableByUser = true,
            writableByUser = false,
            readableByClient = true,
            writableByClient = false,
            readableWithClientScopes = listOf("users:claims:read")
        )

        val parsed = parser.parseAcl(ctx, null, template, "claims.test", null)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = "email",
                readableByUser = true,
                writableByUser = false,
                readableByClient = true,
                writableByClient = false,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = emptyList()
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
                readableByUser = false,
                writableByUser = false,
                readableByClient = false,
                writableByClient = false,
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            ),
            parsed
        )
    }

    @Test
    fun `parseAcl - Let the properties override the template`() {
        val ctx = ConfigParsingContext()

        val parsed = parser.parseAcl(
            ctx,
            aclProperties(readableByUser = "false"),
            template(readableByUser = true),
            "claims.test",
            null
        )

        assertFalse(ctx.hasErrors)
        assertEquals(false, parsed.readableByUser)
    }

    @Test
    fun `parseAcl - Report every boolean that did not parse`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByUser = "not_a_boolean", writableByUser = "also_bad")

        parser.parseAcl(ctx, acl, null, "claims.test", null)

        assertEquals(listOf("config.invalid_boolean", "config.invalid_boolean"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf(
                "claims.test.acl.readable-by-user-when-consented",
                "claims.test.acl.writable-by-user-when-consented"
            ),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `parseGeneratedClaimAcl - Read the readable client scopes from the properties`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableWithClientScopes = listOf("users:claims:read"))

        val parsed = parser.parseGeneratedClaimAcl(ctx, acl, null, "claims.sub")

        assertFalse(ctx.hasErrors)
        assertEquals(
            ParsedClaimAcl(
                consentScope = null,
                readableByUser = null,
                writableByUser = null,
                readableByClient = null,
                writableByClient = null,
                readableWithClientScopes = listOf("users:claims:read"),
                writableWithClientScopes = emptyList()
            ),
            parsed
        )
    }

    @Test
    fun `parseGeneratedClaimAcl - Fall back to the template for the readable client scopes`() {
        val ctx = ConfigParsingContext()
        val template = template(readableWithClientScopes = listOf("users:claims:read"))

        val parsed = parser.parseGeneratedClaimAcl(ctx, null, template, "claims.sub")

        assertFalse(ctx.hasErrors)
        assertEquals(listOf("users:claims:read"), parsed.readableWithClientScopes)
    }
}
