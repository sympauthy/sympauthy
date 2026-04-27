package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.model.EnabledScopesConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.parsing.ClaimAclParser
import com.sympauthy.config.properties.ClaimAclProperties
import com.sympauthy.config.validation.ClaimAclValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaimAclFactoryTest {

    private val parser = ConfigParser()
    private val scopesConfig: ScopesConfig = EnabledScopesConfig(emptyList())

    lateinit var claimAclParser: ClaimAclParser
    lateinit var claimAclValidator: ClaimAclValidator

    @BeforeEach
    fun setUp() {
        claimAclParser = ClaimAclParser(parser)
        claimAclValidator = ClaimAclValidator(scopesConfig)
    }

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
        acl: ClaimTemplateAcl = ClaimTemplateAcl(null, null, null, null, null, null, null)
    ) = ClaimTemplate(
        id = "test",
        enabled = null,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = null,
        acl = acl
    )

    // region parseAcl + validateAcl

    @Test
    fun `buildAcl - Resolves all fields from properties`() {
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

        val parsed = claimAclParser.parseAcl(ctx, acl, null, "claims.test", null)
        val result = claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals("profile", result.consent.scope)
        assertTrue(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertTrue(result.consent.readableByClient)
        assertFalse(result.consent.writableByClient)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
        assertEquals(listOf("users:claims:write"), result.unconditional.writableWithClientScopes)
    }

    @Test
    fun `buildAcl - Falls back to template when properties are null`() {
        val ctx = ConfigParsingContext()
        val template = template(
            acl = ClaimTemplateAcl(
                consentScope = "email",
                readableByUserWhenConsented = true,
                writableByUserWhenConsented = false,
                readableByClientWhenConsented = true,
                writableByClientWhenConsented = false,
                readableWithClientScopesUnconditionally = listOf("users:claims:read"),
                writableWithClientScopesUnconditionally = null
            )
        )

        val parsed = claimAclParser.parseAcl(ctx, null, template, "claims.test", null)
        val result = claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals("email", result.consent.scope)
        assertTrue(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertTrue(result.consent.readableByClient)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
        assertTrue(result.unconditional.writableWithClientScopes.isEmpty())
    }

    @Test
    fun `buildAcl - Falls back to defaultConsentScope when neither properties nor template set scope`() {
        val ctx = ConfigParsingContext()

        val parsed = claimAclParser.parseAcl(ctx, null, null, "claims.test", "profile")
        val result = claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals("profile", result.consent.scope)
    }

    @Test
    fun `buildAcl - Defaults to false and empty when nothing set`() {
        val ctx = ConfigParsingContext()

        val parsed = claimAclParser.parseAcl(ctx, null, null, "claims.test", null)
        val result = claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertFalse(ctx.hasErrors)
        assertNull(result.consent.scope)
        assertFalse(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertFalse(result.consent.readableByClient)
        assertFalse(result.consent.writableByClient)
        assertTrue(result.unconditional.readableWithClientScopes.isEmpty())
        assertTrue(result.unconditional.writableWithClientScopes.isEmpty())
    }

    @Test
    fun `buildAcl - Properties override template`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByUser = "false")
        val template = template(
            acl = ClaimTemplateAcl(
                consentScope = null,
                readableByUserWhenConsented = true,
                writableByUserWhenConsented = null,
                readableByClientWhenConsented = null,
                writableByClientWhenConsented = null,
                readableWithClientScopesUnconditionally = null,
                writableWithClientScopesUnconditionally = null
            )
        )

        val parsed = claimAclParser.parseAcl(ctx, acl, template, "claims.test", null)
        val result = claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertFalse(ctx.hasErrors)
        assertFalse(result.consent.readableByUser)
    }

    @Test
    fun `buildAcl - Accumulates error for invalid boolean`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByUser = "not_a_boolean", writableByUser = "also_bad")

        claimAclParser.parseAcl(ctx, acl, null, "claims.test", null)

        assertEquals(2, ctx.errors.size)
    }

    @Test
    fun `buildAcl - Error for unknown consent scope`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(consentScope = "nonexistent_scope")

        val parsed = claimAclParser.parseAcl(ctx, acl, null, "claims.test", null)
        claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertEquals(1, ctx.errors.size)
    }

    @Test
    fun `buildAcl - Error for unknown client scope in unconditional list`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableWithClientScopes = listOf("nonexistent:scope"))

        val parsed = claimAclParser.parseAcl(ctx, acl, null, "claims.test", null)
        claimAclValidator.validateAcl(ctx, parsed, "claims.test")

        assertEquals(1, ctx.errors.size)
    }

    // endregion

    // region parseGeneratedClaimAcl + validateGeneratedClaimAcl

    @Test
    fun `buildGeneratedClaimAcl - Always read-only with hardcoded consent`() {
        val ctx = ConfigParsingContext()

        val parsed = claimAclParser.parseGeneratedClaimAcl(ctx, null, null, "claims.sub")
        val result = claimAclValidator.validateGeneratedClaimAcl(ctx, parsed, "claims.sub", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals("profile", result.consent.scope)
        assertTrue(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertTrue(result.consent.readableByClient)
        assertFalse(result.consent.writableByClient)
        assertTrue(result.unconditional.writableWithClientScopes.isEmpty())
    }

    @Test
    fun `buildGeneratedClaimAcl - Resolves readable client scopes from properties`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableWithClientScopes = listOf("users:claims:read"))

        val parsed = claimAclParser.parseGeneratedClaimAcl(ctx, acl, null, "claims.sub")
        val result = claimAclValidator.validateGeneratedClaimAcl(ctx, parsed, "claims.sub", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
    }

    @Test
    fun `buildGeneratedClaimAcl - Falls back to template for readable client scopes`() {
        val ctx = ConfigParsingContext()
        val template = template(
            acl = ClaimTemplateAcl(
                consentScope = null,
                readableByUserWhenConsented = null,
                writableByUserWhenConsented = null,
                readableByClientWhenConsented = null,
                writableByClientWhenConsented = null,
                readableWithClientScopesUnconditionally = listOf("users:claims:read"),
                writableWithClientScopesUnconditionally = null
            )
        )

        val parsed = claimAclParser.parseGeneratedClaimAcl(ctx, null, template, "claims.sub")
        val result = claimAclValidator.validateGeneratedClaimAcl(ctx, parsed, "claims.sub", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
    }

    // endregion

    // region parseTemplateAcl + validateTemplateAcl

    @Test
    fun `buildTemplateAcl - Returns all nulls when acl is null`() {
        val ctx = ConfigParsingContext()

        val parsed = claimAclParser.parseTemplateAcl(ctx, null, "templates.claims.test")
        val result = claimAclValidator.validateTemplateAcl(ctx, parsed, "templates.claims.test")

        assertFalse(ctx.hasErrors)
        assertNull(result.consentScope)
        assertNull(result.readableByUserWhenConsented)
        assertNull(result.writableByUserWhenConsented)
        assertNull(result.readableByClientWhenConsented)
        assertNull(result.writableByClientWhenConsented)
        assertNull(result.readableWithClientScopesUnconditionally)
        assertNull(result.writableWithClientScopesUnconditionally)
    }

    @Test
    fun `buildTemplateAcl - Parses all fields`() {
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

        val parsed = claimAclParser.parseTemplateAcl(ctx, acl, "templates.claims.test")
        val result = claimAclValidator.validateTemplateAcl(ctx, parsed, "templates.claims.test")

        assertFalse(ctx.hasErrors)
        assertEquals("profile", result.consentScope)
        assertEquals(true, result.readableByUserWhenConsented)
        assertEquals(false, result.writableByUserWhenConsented)
        assertEquals(true, result.readableByClientWhenConsented)
        assertEquals(false, result.writableByClientWhenConsented)
        assertEquals(listOf("users:claims:read"), result.readableWithClientScopesUnconditionally)
        assertEquals(listOf("users:claims:write"), result.writableWithClientScopesUnconditionally)
    }

    @Test
    fun `buildTemplateAcl - Accumulates errors for invalid booleans`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(readableByUser = "bad", writableByClient = "worse")

        claimAclParser.parseTemplateAcl(ctx, acl, "templates.claims.test")

        assertEquals(2, ctx.errors.size)
    }

    @Test
    fun `buildTemplateAcl - Error for unknown consent scope`() {
        val ctx = ConfigParsingContext()
        val acl = aclProperties(consentScope = "nonexistent")

        val parsed = claimAclParser.parseTemplateAcl(ctx, acl, "templates.claims.test")
        claimAclValidator.validateTemplateAcl(ctx, parsed, "templates.claims.test")

        assertEquals(1, ctx.errors.size)
    }

    // endregion
}
