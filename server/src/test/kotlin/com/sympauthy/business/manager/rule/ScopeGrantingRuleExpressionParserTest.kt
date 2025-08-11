package com.sympauthy.business.manager.rule

import com.sympauthy.business.manager.user.CollectedClaimManager
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleExpressionParserTest {

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @InjectMockKs
    lateinit var parser: ScopeGrantingRuleExpressionParser

    @Test
    fun `validateExpression - Simple expression is valid`() = runBlocking {
        parser.validateExpression("true")
    }

    @Test
    fun `validateExpression - Expression with custom function`() = runBlocking {
        parser.validateExpression(
            """CLAIM("email") = "test@example.com" && CLAIM_IS_VERIFIED("email")"""
        )
    }
}
