package com.sympauthy.business.manager.rule

import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.DECLINE
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleManagerTest {

    @MockK
    lateinit var scopeGrantingRuleExecutor: ScopeGrantingRuleExpressionExecutor

    @MockK
    lateinit var uncheckedScopeGrantingRulesConfig: Flow<ScopeGrantingRulesConfig>

    @InjectMockKs
    lateinit var scopeGrantingRuleManager: ScopeGrantingRuleManager

    @Test
    fun `mergeResult - no rule applicable to requested scopes`() {
        val unruledScope = mockk<Scope>()
        val result = scopeGrantingRuleManager.mergeResult(
            requestedScopes = listOf(unruledScope),
            results = emptyList()
        )
        assertTrue(result.grantedScopes.isEmpty())
        assertTrue(result.declinedScopes.isEmpty())
    }

    @Test
    fun `mergeResult - decline win over grant at same order`() {
        val scope = mockk<Scope>()

        val grantRule = mockk<ScopeGrantingRule>()
        every { grantRule.order } returns 0
        every { grantRule.behavior } returns GRANT
        val grantResult = ScopeGrantingRuleIsApplicableResult(
            rule = grantRule,
            applicableRequestedScopes = listOf(scope),
            applicable = true
        )

        val declineRule = mockk<ScopeGrantingRule>()
        every { declineRule.order } returns 0
        every { declineRule.behavior } returns DECLINE
        val declineResult = ScopeGrantingRuleIsApplicableResult(
            rule = declineRule,
            applicableRequestedScopes = listOf(scope),
            applicable = true
        )

        val result = scopeGrantingRuleManager.mergeResult(
            requestedScopes = listOf(scope),
            results = listOf(grantResult, declineResult)
        )
        assertEquals(listOf(scope), result.declinedScopes)
        assertTrue(result.grantedScopes.isEmpty())
    }

    @Test
    fun `mergeResult - higer order win over lower order`() {
        val scope = mockk<Scope>()

        val grantRule = mockk<ScopeGrantingRule>()
        every { grantRule.order } returns 1
        every { grantRule.behavior } returns GRANT
        val grantResult = ScopeGrantingRuleIsApplicableResult(
            rule = grantRule,
            applicableRequestedScopes = listOf(scope),
            applicable = true
        )

        val declineRule = mockk<ScopeGrantingRule>()
        every { declineRule.order } returns 0
        every { declineRule.behavior } returns DECLINE
        val declineResult = ScopeGrantingRuleIsApplicableResult(
            rule = declineRule,
            applicableRequestedScopes = listOf(scope),
            applicable = true
        )

        val result = scopeGrantingRuleManager.mergeResult(
            requestedScopes = listOf(scope),
            results = listOf(grantResult, declineResult)
        )
        assertEquals(listOf(scope), result.grantedScopes)
        assertTrue(result.declinedScopes.isEmpty())
    }
}
