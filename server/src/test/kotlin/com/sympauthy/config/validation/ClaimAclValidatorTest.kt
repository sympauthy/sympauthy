package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.parsing.ParsedClaimAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ClaimAclValidatorTest {

    private val validator = ClaimAclValidator()

    /**
     * The consentable scopes a deployment that disabled none of them serves.
     */
    private val scopesById: Map<String, Scope> = OpenIdConnectScope.entries
        .associate { it.scope to ConsentableUserScope(scope = it.scope) }

    private fun parsedAcl(
        consentScope: String? = null,
        readableByUser: Boolean? = null,
        writableByUser: Boolean? = null,
        readableByClient: Boolean? = null,
        writableByClient: Boolean? = null,
        readableWithClientScopes: List<String>? = null,
        writableWithClientScopes: List<String>? = null
    ) = ParsedClaimAcl(
        consentScope = consentScope,
        readableByUser = readableByUser,
        writableByUser = writableByUser,
        readableByClient = readableByClient,
        writableByClient = writableByClient,
        readableWithClientScopes = readableWithClientScopes,
        writableWithClientScopes = writableWithClientScopes
    )

    @Test
    fun `validateTemplateAcl - Return all nulls when nothing was parsed`() {
        val ctx = ConfigParsingContext()

        val result = validator.validateTemplateAcl(ctx, parsedAcl(), "templates.claims.test", scopesById)

        assertFalse(ctx.hasErrors)
        assertEquals(ClaimTemplateAcl(null, null, null, null, null, null, null), result)
    }

    @Test
    fun `validateTemplateAcl - Keep every parsed value`() {
        val ctx = ConfigParsingContext()
        val parsed = parsedAcl(
            consentScope = "profile",
            readableByUser = true,
            writableByUser = false,
            readableByClient = true,
            writableByClient = false,
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val result = validator.validateTemplateAcl(ctx, parsed, "templates.claims.test", scopesById)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ClaimTemplateAcl(
                consentScope = "profile",
                readableByUserWhenConsented = true,
                writableByUserWhenConsented = false,
                readableByClientWhenConsented = true,
                writableByClientWhenConsented = false,
                readableWithClientScopesUnconditionally = listOf("users:claims:read"),
                writableWithClientScopesUnconditionally = listOf("users:claims:write")
            ),
            result
        )
    }

    @Test
    fun `validateTemplateAcl - Reject a consent scope no scope names`() {
        val ctx = ConfigParsingContext()

        validator.validateTemplateAcl(
            ctx, parsedAcl(consentScope = "nonexistent"), "templates.claims.test", scopesById
        )

        assertEquals(listOf("config.claim.acl.not_consentable_scope"), ctx.errors.map { it.messageId })
        assertEquals(listOf("templates.claims.test.acl.consent-scope"), ctx.errors.map { it.key })
    }

    @Test
    fun `validateAcl - Build the model from the parsed values`() {
        val ctx = ConfigParsingContext()
        val parsed = parsedAcl(
            consentScope = "profile",
            readableByUser = true,
            writableByUser = false,
            readableByClient = true,
            writableByClient = false,
            readableWithClientScopes = listOf("users:claims:read"),
            writableWithClientScopes = listOf("users:claims:write")
        )

        val result = validator.validateAcl(ctx, parsed, "claims.test", scopesById)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ClaimAcl(
                consent = ConsentAcl(
                    scope = "profile",
                    readableByUser = true,
                    writableByUser = false,
                    readableByClient = true,
                    writableByClient = false
                ),
                unconditional = UnconditionalAcl(
                    readableWithClientScopes = listOf("users:claims:read"),
                    writableWithClientScopes = listOf("users:claims:write")
                )
            ),
            result
        )
    }

    @Test
    fun `validateAcl - Default to false and an empty list when nothing was parsed`() {
        val ctx = ConfigParsingContext()

        val result = validator.validateAcl(ctx, parsedAcl(), "claims.test", scopesById)

        assertFalse(ctx.hasErrors)
        assertEquals(
            ClaimAcl(
                consent = ConsentAcl(
                    scope = null,
                    readableByUser = false,
                    writableByUser = false,
                    readableByClient = false,
                    writableByClient = false
                ),
                unconditional = UnconditionalAcl(emptyList(), emptyList())
            ),
            result
        )
    }

    @Test
    fun `validateAcl - Reject a consent scope no scope names`() {
        val ctx = ConfigParsingContext()

        validator.validateAcl(ctx, parsedAcl(consentScope = "nonexistent_scope"), "claims.test", scopesById)

        assertEquals(listOf("config.claim.acl.not_consentable_scope"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.test.acl.consent-scope"), ctx.errors.map { it.key })
    }

    @Test
    fun `validateAcl - Reject a consent scope the deployment disabled`() {
        val ctx = ConfigParsingContext()
        val disabledScope = OpenIdConnectScope.EMAIL.scope

        validator.validateAcl(
            ctx, parsedAcl(consentScope = disabledScope), "claims.test",
            scopesById + (disabledScope to DisabledScope(disabledScope, ScopeType.CONSENTABLE))
        )

        assertEquals(listOf("config.claim.acl.disabled_consent_scope"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validateAcl - Reject a custom consent scope the deployment disabled`() {
        val ctx = ConfigParsingContext()

        validator.validateAcl(
            ctx, parsedAcl(consentScope = "my-scope"), "claims.test",
            scopesById + ("my-scope" to DisabledScope("my-scope", ScopeType.CONSENTABLE))
        )

        assertEquals(listOf("config.claim.acl.disabled_consent_scope"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validateAcl - Reject a consent scope that is not consentable, disabled or not`() {
        val ctx = ConfigParsingContext()

        validator.validateAcl(
            ctx, parsedAcl(consentScope = "my-scope"), "claims.test",
            scopesById + ("my-scope" to DisabledScope("my-scope", ScopeType.GRANTABLE))
        )

        assertEquals(listOf("config.claim.acl.not_consentable_scope"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validateAcl - Reject a client scope no client scope names`() {
        val ctx = ConfigParsingContext()

        validator.validateAcl(
            ctx, parsedAcl(readableWithClientScopes = listOf("nonexistent:scope")), "claims.test", scopesById
        )

        assertEquals(listOf("config.claim.acl.not_client_scope"), ctx.errors.map { it.messageId })
        assertEquals(
            listOf("claims.test.acl.readable-with-client-scopes-unconditionally[0]"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validateGeneratedClaimAcl - Read only, with the consent scope it is given`() {
        val ctx = ConfigParsingContext()

        val result = validator.validateGeneratedClaimAcl(ctx, parsedAcl(), "claims.sub", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals(
            ClaimAcl(
                consent = ConsentAcl(
                    scope = "profile",
                    readableByUser = true,
                    writableByUser = false,
                    readableByClient = true,
                    writableByClient = false
                ),
                unconditional = UnconditionalAcl(emptyList(), emptyList())
            ),
            result
        )
    }

    @Test
    fun `validateGeneratedClaimAcl - Keep the parsed readable client scopes`() {
        val ctx = ConfigParsingContext()
        val parsed = parsedAcl(readableWithClientScopes = listOf("users:claims:read"))

        val result = validator.validateGeneratedClaimAcl(ctx, parsed, "claims.sub", "profile")

        assertFalse(ctx.hasErrors)
        assertEquals(listOf("users:claims:read"), result.unconditional.readableWithClientScopes)
    }
}
