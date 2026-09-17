package com.sympauthy.business.manager.actas

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.business.model.rule.ActAsRuleBehavior
import com.sympauthy.business.model.rule.ActAsRuleBehavior.ALLOW
import com.sympauthy.business.model.rule.ActAsRuleBehavior.DENY
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.EnabledActAsRulesConfig
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ActAsRuleManagerTest {

    private val client = mockk<Client>(relaxed = true)

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

    @Test
    fun `isActAsAllowed - a claim of another audience is not bound, so a rule reading it fails closed`() = runTest {
        val allowed = manager(rule(ALLOW, 0, """CLAIM("custom_tier") = "gold"""")).isActAsAllowed(
            actingClientOf("default"),
            listOf(claimOfAnotherAudience(value = "gold"))
        )

        assertFalse(allowed)
    }

    @Test
    fun `isActAsAllowed - a claim of the acting client's own audience is bound`() = runTest {
        val allowed = manager(rule(ALLOW, 0, """CLAIM("custom_tier") = "gold"""")).isActAsAllowed(
            actingClientOf("default"),
            listOf(boundClaim("custom_tier", audienceId = "default", value = "gold"))
        )

        assertTrue(allowed)
    }

    private fun actingClientOf(audienceId: String) = mockk<Client> {
        every { audience } returns Audience(id = audienceId, tokenAudience = "https://$audienceId.example.com")
    }

    /** A claim the rules are given, whose id the executor reads to key its value by. */
    private fun boundClaim(claimId: String, audienceId: String, value: String) = collectedClaim(
        value,
        mockk {
            every { id } returns claimId
            every { belongsToAudience(any()) } answers { audienceId == firstArg() }
        }
    )

    /** A claim the audience narrowing drops, so nothing downstream ever asks it for an id. */
    private fun claimOfAnotherAudience(value: String) = collectedClaim(
        value,
        mockk { every { belongsToAudience(any()) } returns false }
    )

    private fun collectedClaim(value: String, claim: Claim) = CollectedClaim(
        userId = UUID.randomUUID(),
        claim = claim,
        value = value,
        verified = null,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )
}
