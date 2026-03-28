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
    fun `validateUserExpression - Simple expression is valid`() = runTest {
        parser.validateUserExpression("true")
    }

    @Test
    fun `validateUserExpression - Expression with custom function`() = runTest {
        parser.validateUserExpression(
            """CLAIM("email") = "test@example.com" && CLAIM_IS_VERIFIED("email")"""
        )
    }

    @Test
    fun `validateClientExpression - Simple expression is valid`() = runTest {
        parser.validateClientExpression("true")
    }

    @Test
    fun `validateClientExpression - Expression with CLIENT function`() = runTest {
        parser.validateClientExpression(
            """CLIENT("id") = "my-client-id""""
        )
    }
}
