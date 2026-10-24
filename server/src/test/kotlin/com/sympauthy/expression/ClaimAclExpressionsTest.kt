package com.sympauthy.expression

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.claim.ClaimAclContext
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class ClaimAclExpressionsTest {

    private val client = Client(
        id = "billing",
        secret = null,
        audience = Audience(id = "default", tokenAudience = "default"),
        allowedGrantTypes = emptySet(),
        authorizationFlow = null
    )

    private fun context(
        consentedScopes: List<String> = emptyList(),
        grantedScopes: List<String> = emptyList(),
        authenticationDate: LocalDateTime? = null,
        bound: Boolean = false
    ) = ClaimAclContext(client, consentedScopes, grantedScopes, authenticationDate, bound)

    private fun evaluateToken(expression: String, context: ClaimAclContext) =
        evaluateRuleExpression(expression, ClaimAclExpressions.tokenConfiguration(context, NOW))

    @Test
    fun `validateTokenExpression - Return every scope, duration and binding the expression names`() {
        val terms = ClaimAclExpressions.validateTokenExpression(
            """SCOPE_IS_CONSENTED("profile") && TOKEN_IS_BOUND() && AUTHENTICATION_IS_YOUNGER_THAN("5m") """ +
                    """|| SCOPE_IS_GRANTED("users:claims:read")"""
        )

        assertEquals(setOf("profile"), terms.consentedScopes)
        assertEquals(setOf("users:claims:read"), terms.grantedScopes)
        assertEquals(setOf(Duration.ofMinutes(5)), terms.authenticationAges)
        assertTrue(terms.asksForBoundToken)
    }

    @Test
    fun `validateTokenExpression - Read a call no evaluation reaches`() {
        // Every function answers false at validation, so `&&` never reaches the second operand.
        val terms = ClaimAclExpressions.validateTokenExpression(
            """SCOPE_IS_CONSENTED("profile") && SCOPE_IS_CONSENTED("profile:write")"""
        )

        assertEquals(setOf("profile", "profile:write"), terms.consentedScopes)
    }

    @Test
    fun `validateTokenExpression - Name nothing for a literal`() {
        val terms = ClaimAclExpressions.validateTokenExpression("true")

        assertTrue(terms.consentedScopes.isEmpty())
        assertFalse(terms.asksForBoundToken)
    }

    @Test
    fun `validateTokenExpression - Refuse a scope function given no argument`() {
        val exception = assertThrows<InvalidRuleExpressionException> {
            ClaimAclExpressions.validateTokenExpression("SCOPE_IS_CONSENTED()")
        }
        assertEquals("config.expression.invalid", exception.configMessageId)
    }

    @Test
    fun `validateTokenExpression - Refuse a scope written as anything but a literal`() {
        val exception = assertThrows<InvalidRuleExpressionException> {
            ClaimAclExpressions.validateTokenExpression("""SCOPE_IS_CONSENTED(CLIENT("audience"))""")
        }
        assertEquals("config.expression.invalid", exception.configMessageId)
    }

    @Test
    fun `validateTokenExpression - Refuse an expression that is not a boolean`() {
        val exception = assertThrows<InvalidRuleExpressionException> {
            ClaimAclExpressions.validateTokenExpression("""CLIENT("id")""")
        }
        assertEquals("config.expression.invalid_return", exception.configMessageId)
    }

    @Test
    fun `validateFlowExpression - Accept the consent and the client`() {
        val terms = ClaimAclExpressions.validateFlowExpression(
            """SCOPE_IS_CONSENTED("profile") && CLIENT("id") = "billing""""
        )

        assertEquals(setOf("profile"), terms.consentedScopes)
    }

    @Test
    fun `validateFlowExpression - Refuse a function of the token`() {
        listOf("TOKEN_IS_BOUND()", """AUTHENTICATION_IS_YOUNGER_THAN("5m")""", """SCOPE_IS_GRANTED("premium")""")
            .forEach {
                val exception = assertThrows<InvalidRuleExpressionException>(it) {
                    ClaimAclExpressions.validateFlowExpression(it)
                }
                assertEquals("config.expression.invalid", exception.configMessageId)
            }
    }

    @Test
    fun `flowConfiguration - Answer the consent and the client the flow carries`() {
        val configuration = ClaimAclExpressions.flowConfiguration(client, listOf("profile"))

        assertTrue(evaluateRuleExpression("""SCOPE_IS_CONSENTED("profile")""", configuration))
        assertFalse(evaluateRuleExpression("""SCOPE_IS_CONSENTED("email")""", configuration))
        assertTrue(evaluateRuleExpression("""CLIENT("id") = "billing"""", configuration))
    }

    @Test
    fun `tokenConfiguration - Answer a consented scope apart from a granted one`() {
        val context = context(consentedScopes = listOf("profile"), grantedScopes = listOf("premium"))

        assertTrue(evaluateToken("""SCOPE_IS_CONSENTED("profile")""", context))
        assertFalse(evaluateToken("""SCOPE_IS_GRANTED("profile")""", context))
        assertTrue(evaluateToken("""SCOPE_IS_GRANTED("premium")""", context))
        assertFalse(evaluateToken("""SCOPE_IS_CONSENTED("premium")""", context))
    }

    @Test
    fun `tokenConfiguration - Answer the age of the authentication behind the token`() {
        val expression = """AUTHENTICATION_IS_YOUNGER_THAN("5m")"""

        assertTrue(evaluateToken(expression, context(authenticationDate = NOW.minusMinutes(5))))
        assertFalse(evaluateToken(expression, context(authenticationDate = NOW.minusMinutes(6))))
        assertFalse(evaluateToken(expression, context(authenticationDate = null)))
    }

    @Test
    fun `tokenConfiguration - Read the duration the way every duration key is`() {
        val context = context(authenticationDate = NOW.minusMinutes(90))

        assertTrue(evaluateToken("""AUTHENTICATION_IS_YOUNGER_THAN("2h")""", context))
        assertTrue(evaluateToken("""AUTHENTICATION_IS_YOUNGER_THAN("PT2H")""", context))
        assertFalse(evaluateToken("""AUTHENTICATION_IS_YOUNGER_THAN("1h")""", context))
    }

    @Test
    fun `validateTokenExpression - Refuse a duration that does not read, where evaluation reaches it or not`() {
        listOf(
            """AUTHENTICATION_IS_YOUNGER_THAN("soon")""",
            // Every function answers false at validation, so `&&` never reaches the second operand.
            """SCOPE_IS_CONSENTED("profile") && AUTHENTICATION_IS_YOUNGER_THAN("soon")"""
        ).forEach {
            val exception = assertThrows<InvalidRuleExpressionException>(it) {
                ClaimAclExpressions.validateTokenExpression(it)
            }
            assertEquals("config.expression.invalid", exception.configMessageId)
        }
    }

    @Test
    fun `tokenConfiguration - Answer whether the token is bound`() {
        assertTrue(evaluateToken("TOKEN_IS_BOUND()", context(bound = true)))
        assertFalse(evaluateToken("TOKEN_IS_BOUND()", context(bound = false)))
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 6, 1, 12, 0, 0)
    }
}
