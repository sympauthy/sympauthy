package com.sympauthy.business.manager.actas

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActAsRuleExpressionExecutorTest {

    private val executor = ActAsRuleExpressionExecutor()

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `validateExpression - simple expression is valid`() = runTest {
        executor.validateExpression("true")
    }

    @Test
    fun `validateExpression - expression mixing CLIENT and CLAIM functions is valid`() = runTest {
        executor.validateExpression(
            """CLIENT("client_id") = "discord-bot" && CLIENT("audience") = "admin" """ +
                """&& CLAIM("email") = "a@b.c" && CLAIM_IS_VERIFIED("email")"""
        )
    }

    @Test
    fun `getConfiguration - CLIENT resolves client_id and audience`() = runTest {
        val client = mockk<Client> {
            every { id } returns "discord-bot"
            every { audience } returns mockk { every { id } returns "admin" }
            every { public } returns false
        }
        val config = executor.getConfiguration(client, emptyList())
        assertTrue(executor.evaluateExpressionOrThrow("""CLIENT("client_id") = "discord-bot"""", config))
        assertTrue(executor.evaluateExpressionOrThrow("""CLIENT("audience") = "admin"""", config))
        assertFalse(executor.evaluateExpressionOrThrow("""CLIENT("audience") = "other"""", config))
    }

    @Test
    fun `getConfiguration - CLAIM_IS_VERIFIED reads the verified flag`() = runTest {
        val client = mockk<Client>(relaxed = true)
        val claims = listOf(
            collectedClaim("discord_id", value = "123", verified = true),
            collectedClaim("email", value = "a@b.c", verified = false)
        )
        val config = executor.getConfiguration(client, claims)
        assertTrue(executor.evaluateExpressionOrThrow("""CLAIM_IS_VERIFIED("discord_id")""", config))
        assertFalse(executor.evaluateExpressionOrThrow("""CLAIM_IS_VERIFIED("email")""", config))
        assertFalse(executor.evaluateExpressionOrThrow("""CLAIM_IS_VERIFIED("missing")""", config))
    }

    private fun collectedClaim(claimId: String, value: String?, verified: Boolean?): CollectedClaim {
        val claim = mockk<Claim>()
        every { claim.id } returns claimId
        return CollectedClaim(
            userId = UUID.randomUUID(),
            claim = claim,
            value = value,
            verified = verified,
            collectionDate = LocalDateTime.now(),
            verificationDate = null
        )
    }
}
