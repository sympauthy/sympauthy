package com.sympauthy.expression

import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

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

    @Test
    fun `validateClientExpression - Refuse a function only an end-user rule may call`() {
        assertThrows<InvalidRuleExpressionException> {
            ScopeGrantingRuleExpressions.validateClientExpression("""CLAIM("email") = "a@b.c"""")
        }
    }

    @Test
    fun `validateUserExpression - Refuse a function only a client rule may call`() {
        assertThrows<InvalidRuleExpressionException> {
            ScopeGrantingRuleExpressions.validateUserExpression("""CLIENT("id") = "my-client-id"""")
        }
    }

    /**
     * EvalEx registers a function by mutating the configuration it is given, so a configuration kept
     * between calls would let one request's values answer another request's expression.
     */
    @Test
    fun `userConfiguration - Bind claims to this configuration and no other`() {
        val first = ScopeGrantingRuleExpressions.userConfiguration(
            listOf(collectedClaim("email", value = "first@example.com"))
        )
        val second = ScopeGrantingRuleExpressions.userConfiguration(
            listOf(collectedClaim("email", value = "second@example.com"))
        )

        assertTrue(evaluateRuleExpression("""CLAIM("email") = "first@example.com"""", first))
        assertTrue(evaluateRuleExpression("""CLAIM("email") = "second@example.com"""", second))
    }

    private fun collectedClaim(claimId: String, value: String): CollectedClaim {
        val claim = mockk<Claim> { every { id } returns claimId }
        return CollectedClaim(
            userId = UUID.randomUUID(),
            claim = claim,
            value = value,
            verified = true,
            collectionDate = LocalDateTime.now(),
            verificationDate = null
        )
    }
}
