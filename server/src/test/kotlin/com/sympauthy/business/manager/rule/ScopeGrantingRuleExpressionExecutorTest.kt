package com.sympauthy.business.manager.rule

import com.sympauthy.business.manager.user.CollectedClaimManager
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleExpressionExecutorTest {

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @InjectMockKs
    lateinit var parser: ScopeGrantingRuleExpressionExecutor

    @Test
    fun `validateExpression - Simple expression is valid`() = runTest {
        parser.validateExpression("true")
    }

    @Test
    fun `validateExpression - Expression with custom function`() = runTest {
        parser.validateExpression(
            """CLAIM("email") = "test@example.com" && CLAIM_IS_VERIFIED("email")"""
        )
    }
}
