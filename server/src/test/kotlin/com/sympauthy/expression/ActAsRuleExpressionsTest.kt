package com.sympauthy.expression

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ActAsRuleExpressionsTest {

    @Test
    fun `validateExpression - Accept a simple expression`() {
        ActAsRuleExpressions.validateExpression("true")
    }

    @Test
    fun `validateExpression - Accept an expression mixing the client and claim functions`() {
        ActAsRuleExpressions.validateExpression(
            """CLIENT("client_id") = "discord-bot" && CLIENT("audience") = "admin" """ +
                """&& CLAIM("email") = "a@b.c" && CLAIM_IS_VERIFIED("email")"""
        )
    }

    @Test
    fun `validateExpression - Refuse an expression that does not parse`() {
        assertThrows<InvalidRuleExpressionException> {
            ActAsRuleExpressions.validateExpression("""CLIENT("client_id" = """)
        }
    }

    @Test
    fun `validateExpression - Refuse an expression that is not a boolean`() {
        assertThrows<InvalidRuleExpressionException> {
            ActAsRuleExpressions.validateExpression("1 + 1")
        }
    }
}
