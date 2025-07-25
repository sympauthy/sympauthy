package com.sympauthy.business.manager.rule

import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ScopeGrantingRuleManagerTest {

    @MockK
    lateinit var uncheckedScopeGrantingRulesConfig: ScopeGrantingRulesConfig

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
}