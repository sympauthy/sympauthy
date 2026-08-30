package com.sympauthy.expression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuleExpressionTest {

    @Test
    fun `evaluateRuleExpression - Return what a boolean expression answers`() {
        assertTrue(evaluateRuleExpression("1 = 1", defaultExpressionConfiguration()))
    }

    @Test
    fun `evaluateRuleExpression - Refuse an expression that does not parse, naming what refused it`() {
        val exception = assertThrows<InvalidRuleExpressionException> {
            evaluateRuleExpression("1 +", defaultExpressionConfiguration())
        }

        assertEquals("config.rule.expression.invalid", exception.configMessageId)
        assertTrue(exception.reason.startsWith("1 + - "), exception.reason)
    }

    @Test
    fun `evaluateRuleExpression - Refuse an expression that does not answer a boolean`() {
        val exception = assertThrows<InvalidRuleExpressionException> {
            evaluateRuleExpression("1 + 1", defaultExpressionConfiguration())
        }

        assertEquals("config.rule.expression.invalid_return", exception.configMessageId)
        assertEquals("1 + 1", exception.reason)
    }
}
