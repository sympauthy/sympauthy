package com.sympauthy.business.model.rule

import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleTest {

    @MockK
    lateinit var scope: Scope

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
}
