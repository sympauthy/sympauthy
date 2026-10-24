package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.parsing.ParsedClaimAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class ClaimAclValidatorTest {

    private val validator = ClaimAclValidator()

    /**
     * The consentable scopes a deployment that disabled none of them serves, a grantable one a rule may grant,
     * and the client scope a client holds.
     */
    private val scopesById: Map<String, Scope> = OpenIdConnectScope.entries
        .associate<OpenIdConnectScope, String, Scope> { it.scope to ConsentableUserScope(scope = it.scope) } +
            mapOf(
                "premium" to GrantableUserScope(scope = "premium", publishedIn = emptySet()),
                "users:claims:read" to ClientScope(scope = "users:claims:read")
            )

    private fun parsedAcl(
        collectedInFlow: String? = null,
        readableByPerson: String? = null,
        writableByPerson: String? = null,
        readableByClient: String? = null,
        writableByClient: String? = null
    ) = ParsedClaimAcl(collectedInFlow, readableByPerson, writableByPerson, readableByClient, writableByClient)

    private fun validate(parsed: ParsedClaimAcl, ctx: ConfigParsingContext = ConfigParsingContext()): ClaimAcl =
        validator.validateAcl(ctx, parsed, "claims.test", scopesById)

    private fun errorsOf(parsed: ParsedClaimAcl, scopes: Map<String, Scope> = scopesById): List<Pair<String, String>> {
        val ctx = ConfigParsingContext()
        validator.validateAcl(ctx, parsed, "claims.test", scopes)
        return ctx.errors.map { it.key to it.messageId }
    }

    @Test
    fun `validateAcl - Build every door from the expression it was written with`() {
        val ctx = ConfigParsingContext()

        val result = validate(
            parsedAcl(
                collectedInFlow = """SCOPE_IS_CONSENTED("profile")""",
                readableByClient = """SCOPE_IS_CONSENTED("profile") || SCOPE_IS_GRANTED("users:claims:read")""",
                writableByPerson = PHONE_WRITE
            ),
            ctx
        )

        assertFalse(ctx.hasErrors)
        assertEquals(
            ClaimAcl(
                collectedInFlow = ClaimAclExpression("""SCOPE_IS_CONSENTED("profile")""", setOf("profile")),
                readableByPerson = null,
                writableByPerson = ClaimAclExpression(
                    PHONE_WRITE,
                    consentedScopes = setOf("phone"),
                    authenticationAges = setOf(Duration.ofMinutes(5)),
                    asksForBoundToken = true
                ),
                readableByClient = ClaimAclExpression(
                    """SCOPE_IS_CONSENTED("profile") || SCOPE_IS_GRANTED("users:claims:read")""",
                    consentedScopes = setOf("profile")
                ),
                writableByClient = null
            ),
            result
        )
    }

    @Test
    fun `validateAcl - Shut a door written as the literal false`() {
        assertNull(validate(parsedAcl(writableByClient = "false")).writableByClient)
    }

    @Test
    fun `validateAcl - Refuse an expression that does not parse, naming its key`() {
        assertEquals(
            listOf("claims.test.acl.readable-by-client" to "config.expression.invalid"),
            errorsOf(parsedAcl(readableByClient = """SCOPE_IS_CONSENTED("profile" ||"""))
        )
    }

    @Test
    fun `validateAcl - Refuse an expression that is not a boolean`() {
        assertEquals(
            listOf("claims.test.acl.readable-by-person" to "config.expression.invalid_return"),
            errorsOf(parsedAcl(readableByPerson = """CLIENT("id")"""))
        )
    }

    @Test
    fun `validateAcl - Refuse a function of the token on the flow's door, and accept it on a token's`() {
        assertEquals(
            listOf("claims.test.acl.collected-in-flow" to "config.expression.invalid"),
            errorsOf(parsedAcl(collectedInFlow = "TOKEN_IS_BOUND()", writableByPerson = "TOKEN_IS_BOUND()"))
        )
    }

    @Test
    fun `validateAcl - Refuse a consented scope that is not consentable`() {
        assertEquals(
            listOf("claims.test.acl.readable-by-person" to "config.claim.acl.not_consentable_scope"),
            errorsOf(parsedAcl(readableByPerson = """SCOPE_IS_CONSENTED("admin:users:read")"""))
        )
    }

    @Test
    fun `validateAcl - Refuse a consented scope the deployment turned off`() {
        val scopes = scopesById + ("profile" to DisabledScope(scope = "profile", type = ScopeType.CONSENTABLE))

        assertEquals(
            listOf("claims.test.acl.readable-by-person" to "config.claim.acl.disabled_consent_scope"),
            errorsOf(parsedAcl(readableByPerson = """SCOPE_IS_CONSENTED("profile")"""), scopes)
        )
    }

    @Test
    fun `validateAcl - Refuse a granted scope no rule grants`() {
        assertEquals(
            listOf("claims.test.acl.readable-by-client" to "config.claim.acl.not_grantable_scope"),
            errorsOf(parsedAcl(readableByClient = """SCOPE_IS_GRANTED("profile")"""))
        )
    }

    @Test
    fun `validateAcl - Accept a grantable scope and a client scope as granted`() {
        assertEquals(
            emptyList<Pair<String, String>>(),
            errorsOf(
                parsedAcl(
                    writableByPerson = """SCOPE_IS_GRANTED("premium")""",
                    readableByClient = """SCOPE_IS_GRANTED("users:claims:read")"""
                )
            )
        )
    }

    @Test
    fun `validateAcl - Refuse an authentication age that is not positive`() {
        assertEquals(
            listOf("claims.test.acl.writable-by-person" to "config.claim.acl.authentication_age.not_positive"),
            errorsOf(parsedAcl(writableByPerson = """AUTHENTICATION_IS_YOUNGER_THAN("0s")"""))
        )
    }

    @Test
    fun `validateAcl - Refuse an authentication age that is not a duration`() {
        assertEquals(
            listOf("claims.test.acl.writable-by-person" to "config.expression.invalid"),
            errorsOf(parsedAcl(writableByPerson = """AUTHENTICATION_IS_YOUNGER_THAN("soon")"""))
        )
    }

    @Test
    fun `validateTemplateAcl - Keep the expressions as written and refuse one under the template's key`() {
        val ctx = ConfigParsingContext()

        val result = validator.validateTemplateAcl(
            ctx,
            parsedAcl(
                readableByClient = """SCOPE_IS_GRANTED("users:claims:read")""",
                writableByClient = "TOKEN_IS_BOUND("
            ),
            "templates.claims.test",
            scopesById
        )

        assertEquals(
            ClaimTemplateAcl(null, null, null, """SCOPE_IS_GRANTED("users:claims:read")""", "TOKEN_IS_BOUND("),
            result
        )
        assertEquals(
            listOf("templates.claims.test.acl.writable-by-client" to "config.expression.invalid"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    private companion object {
        const val PHONE_WRITE =
            """SCOPE_IS_CONSENTED("phone") && TOKEN_IS_BOUND() && AUTHENTICATION_IS_YOUNGER_THAN("5m")"""
    }
}
