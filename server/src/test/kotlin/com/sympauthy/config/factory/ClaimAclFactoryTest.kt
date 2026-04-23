package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.model.EnabledScopesConfig
import com.sympauthy.config.properties.ClaimAclProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClaimAclFactoryTest {

    private val parser = ConfigParser()
    private val scopesConfig: com.sympauthy.config.model.ScopesConfig = EnabledScopesConfig(emptyList())

    lateinit var factory: ClaimAclFactory

    @BeforeEach
    fun setUp() {
        factory = ClaimAclFactory(parser, scopesConfig)
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
        allowedValues = null,
        acl = acl
    )

    // region buildAcl

    @Test
    fun `buildAcl - Resolves all fields from properties`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(
            consentScope = "profile",
            readableByUser = "true",
            writableByUser = "false",
            readableByClient = "true",
            writableByClient = "false",
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val result = factory.buildAcl(acl, null, "claims.test", null, errors)

        assertTrue(errors.isEmpty())
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
        val errors = mutableListOf<ConfigurationException>()
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

        val result = factory.buildAcl(null, template, "claims.test", null, errors)

        assertTrue(errors.isEmpty())
        assertEquals("email", result.consent.scope)
        assertTrue(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertTrue(result.consent.readableByClient)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
        assertTrue(result.unconditional.writableWithClientScopes.isEmpty())
    }

    @Test
    fun `buildAcl - Falls back to defaultConsentScope when neither properties nor template set scope`() {
        val errors = mutableListOf<ConfigurationException>()

        val result = factory.buildAcl(null, null, "claims.test", "profile", errors)

        assertTrue(errors.isEmpty())
        assertEquals("profile", result.consent.scope)
    }

    @Test
    fun `buildAcl - Defaults to false and empty when nothing set`() {
        val errors = mutableListOf<ConfigurationException>()

        val result = factory.buildAcl(null, null, "claims.test", null, errors)

        assertTrue(errors.isEmpty())
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
        val errors = mutableListOf<ConfigurationException>()
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

        val result = factory.buildAcl(acl, template, "claims.test", null, errors)

        assertTrue(errors.isEmpty())
        assertFalse(result.consent.readableByUser)
    }

    @Test
    fun `buildAcl - Accumulates error for invalid boolean`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(readableByUser = "not_a_boolean", writableByUser = "also_bad")

        factory.buildAcl(acl, null, "claims.test", null, errors)

        assertEquals(2, errors.size)
    }

    @Test
    fun `buildAcl - Error for unknown consent scope`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(consentScope = "nonexistent_scope")

        factory.buildAcl(acl, null, "claims.test", null, errors)

        assertEquals(1, errors.size)
    }

    @Test
    fun `buildAcl - Error for unknown client scope in unconditional list`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(readableWithClientScopes = listOf("nonexistent:scope"))

        factory.buildAcl(acl, null, "claims.test", null, errors)

        assertEquals(1, errors.size)
    }

    // endregion

    // region buildGeneratedClaimAcl

    @Test
    fun `buildGeneratedClaimAcl - Always read-only with hardcoded consent`() {
        val errors = mutableListOf<ConfigurationException>()

        val result = factory.buildGeneratedClaimAcl(null, null, "claims.sub", "profile", errors)

        assertTrue(errors.isEmpty())
        assertEquals("profile", result.consent.scope)
        assertTrue(result.consent.readableByUser)
        assertFalse(result.consent.writableByUser)
        assertTrue(result.consent.readableByClient)
        assertFalse(result.consent.writableByClient)
        assertTrue(result.unconditional.writableWithClientScopes.isEmpty())
    }

    @Test
    fun `buildGeneratedClaimAcl - Resolves readable client scopes from properties`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(readableWithClientScopes = listOf("users:claims:read"))

        val result = factory.buildGeneratedClaimAcl(acl, null, "claims.sub", "profile", errors)

        assertTrue(errors.isEmpty())
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
    }

    @Test
    fun `buildGeneratedClaimAcl - Falls back to template for readable client scopes`() {
        val errors = mutableListOf<ConfigurationException>()
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

        val result = factory.buildGeneratedClaimAcl(null, template, "claims.sub", "profile", errors)

        assertTrue(errors.isEmpty())
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
    }

    // endregion

    // region buildTemplateAcl

    @Test
    fun `buildTemplateAcl - Returns all nulls when acl is null`() {
        val errors = mutableListOf<ConfigurationException>()

        val result = factory.buildTemplateAcl(null, "templates.claims.test", errors)

        assertTrue(errors.isEmpty())
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
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(
            consentScope = "profile",
            readableByUser = "true",
            writableByUser = "false",
            readableByClient = "true",
            writableByClient = "false",
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val result = factory.buildTemplateAcl(acl, "templates.claims.test", errors)

        assertTrue(errors.isEmpty())
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
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(readableByUser = "bad", writableByClient = "worse")

        factory.buildTemplateAcl(acl, "templates.claims.test", errors)

        assertEquals(2, errors.size)
    }

    @Test
    fun `buildTemplateAcl - Error for unknown consent scope`() {
        val errors = mutableListOf<ConfigurationException>()
        val acl = aclProperties(consentScope = "nonexistent")

        factory.buildTemplateAcl(acl, "templates.claims.test", errors)

        assertEquals(1, errors.size)
    }

    // endregion
}
