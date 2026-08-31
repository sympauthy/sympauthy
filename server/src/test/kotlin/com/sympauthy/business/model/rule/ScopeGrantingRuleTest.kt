package com.sympauthy.business.model.rule

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleTest {

    @MockK
    lateinit var scope: EnabledScope

    @Test
    fun generatedName() {
        every { scope.scope } returns "test"

        val rule = ScopeGrantingRule(
            userDefinedName = null,
            order = 0,
            scopes = listOf(scope),
            behavior = GRANT,
            expressions = emptyList(),
        )
        assertEquals("0 - GRANT test", rule.generatedName)
    }

    @Test
    fun `getApplicableScopes - Return scopes present in both lists`() {
        val scope1 = mockk<EnabledScope>()
        val scope2 = mockk<EnabledScope>()
        val scope3 = mockk<EnabledScope>()

        val rule = ScopeGrantingRule(
            userDefinedName = null,
            order = 0,
            scopes = listOf(scope1, scope2),
            behavior = GRANT,
            expressions = emptyList(),
        )

        val result = rule.getApplicableScopes(listOf(scope1, scope3))

        assertEquals(listOf(scope1), result)
    }

    @Test
    fun `getApplicableScopes - Return all matching scopes`() {
        val scope1 = mockk<EnabledScope>()
        val scope2 = mockk<EnabledScope>()
        val scope3 = mockk<EnabledScope>()

        val rule = ScopeGrantingRule(
            userDefinedName = null,
            order = 0,
            scopes = listOf(scope1, scope2, scope3),
            behavior = GRANT,
            expressions = emptyList(),
        )

        val result = rule.getApplicableScopes(listOf(scope1, scope2, scope3))

        assertEquals(listOf(scope1, scope2, scope3), result)
    }

    @Test
    fun `getApplicableScopes - Return empty list when no scopes match`() {
        val scope1 = mockk<EnabledScope>()
        val scope2 = mockk<EnabledScope>()
        val scope3 = mockk<EnabledScope>()

        val rule = ScopeGrantingRule(
            userDefinedName = null,
            order = 0,
            scopes = listOf(scope1, scope2),
            behavior = GRANT,
            expressions = emptyList(),
        )

        val result = rule.getApplicableScopes(listOf(scope3))

        assertEquals(emptyList(), result)
    }

    @Test
    fun `getApplicableScopes - Return empty list when requested scopes is empty`() {
        val scope1 = mockk<EnabledScope>()

        val rule = ScopeGrantingRule(
            userDefinedName = null,
            order = 0,
            scopes = listOf(scope1),
            behavior = GRANT,
            expressions = emptyList(),
        )

        val result = rule.getApplicableScopes(emptyList())

        assertEquals(emptyList(), result)
    }
}
