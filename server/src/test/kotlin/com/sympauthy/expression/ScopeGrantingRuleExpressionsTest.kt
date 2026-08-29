package com.sympauthy.expression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScopeGrantingRuleExpressionsTest {

    @Test
    fun `validateUserExpression - Accept a simple expression`() {
        ScopeGrantingRuleExpressions.validateUserExpression("true")
    }

    @Test
    fun `validateUserExpression - Accept the functions an end-user rule may call`() {
        ScopeGrantingRuleExpressions.validateUserExpression(
            """CLAIM("email") = "test@example.com" && CLAIM_IS_VERIFIED("email")"""
        )
    }

    @Test
    fun `validateUserExpression - Refuse an expression that does not parse`() {
        assertThrows<InvalidRuleExpressionException> {
            ScopeGrantingRuleExpressions.validateUserExpression("""CLAIM("email" = """)
        }
    }

    @Test
    fun `validateUserExpression - Refuse an expression that is not a boolean`() {
        assertThrows<InvalidRuleExpressionException> {
            ScopeGrantingRuleExpressions.validateUserExpression("1 + 1")
        }
    }

    @Test
    fun `validateClientExpression - Accept a simple expression`() {
        ScopeGrantingRuleExpressions.validateClientExpression("true")
    }

    @Test
    fun `validateClientExpression - Accept the functions a client rule may call`() {
        ScopeGrantingRuleExpressions.validateClientExpression("""CLIENT("id") = "my-client-id"""")
    }
}
