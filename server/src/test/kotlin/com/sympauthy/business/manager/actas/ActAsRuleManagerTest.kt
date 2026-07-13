package com.sympauthy.business.manager.actas

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.business.model.rule.ActAsRuleBehavior
import com.sympauthy.business.model.rule.ActAsRuleBehavior.ALLOW
import com.sympauthy.business.model.rule.ActAsRuleBehavior.DENY
import com.sympauthy.config.model.EnabledActAsRulesConfig
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActAsRuleManagerTest {

    private val client = mockk<Client>(relaxed = true)

    @AfterEach
    fun tearDown() = clearAllMocks()

    private fun manager(vararg rules: ActAsRule) = ActAsRuleManager(
        actAsRuleExpressionExecutor = ActAsRuleExpressionExecutor(),
        uncheckedActAsRulesConfigFlow = flowOf(EnabledActAsRulesConfig(rules.toList()))
    )

    private fun rule(behavior: ActAsRuleBehavior, order: Int, vararg expressions: String) = ActAsRule(
        userDefinedName = null,
        behavior = behavior,
        order = order,
        expressions = expressions.toList()
    )

    @Test
    fun `isActAsAllowed - no rule fails closed`() = runTest {
        assertFalse(manager().isActAsAllowed(client, emptyList()))
    }

    @Test
    fun `isActAsAllowed - no matching rule fails closed`() = runTest {
        assertFalse(manager(rule(ALLOW, 0, "false")).isActAsAllowed(client, emptyList()))
    }

    @Test
    fun `isActAsAllowed - matching allow rule allows`() = runTest {
        assertTrue(manager(rule(ALLOW, 0, "true")).isActAsAllowed(client, emptyList()))
    }

    @Test
    fun `isActAsAllowed - matching deny rule denies`() = runTest {
        assertFalse(manager(rule(DENY, 0, "true")).isActAsAllowed(client, emptyList()))
    }

    @Test
    fun `isActAsAllowed - deny wins over allow at same order`() = runTest {
        assertFalse(
            manager(
                rule(ALLOW, 0, "true"),
                rule(DENY, 0, "true")
            ).isActAsAllowed(client, emptyList())
        )
    }

    @Test
    fun `isActAsAllowed - higher order allow wins over lower order deny`() = runTest {
        assertTrue(
            manager(
                rule(ALLOW, 1, "true"),
                rule(DENY, 0, "true")
            ).isActAsAllowed(client, emptyList())
        )
    }

    @Test
    fun `isActAsAllowed - all expressions of a rule must match`() = runTest {
        assertFalse(manager(rule(ALLOW, 0, "true", "false")).isActAsAllowed(client, emptyList()))
    }
}
