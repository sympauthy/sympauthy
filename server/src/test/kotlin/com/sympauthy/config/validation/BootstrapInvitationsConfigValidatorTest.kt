package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.BootstrapInvitation
import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrapInvitationsConfigValidatorTest {

    private val validator = BootstrapInvitationsConfigValidator()

    private val audiencesById = mapOf(
        "default" to Audience(id = "default", tokenAudience = "default"),
        "billing" to Audience(id = "billing", tokenAudience = "billing")
    )

    private fun claim(id: String, audienceId: String? = null) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        audienceId = audienceId,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = true,
                writableByUser = true,
                readableByClient = true,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    )

    private fun properties(audience: String?, claims: Map<String, String>?) =
        BootstrapInvitationConfigurationProperties("first-admin").apply {
            this.audience = audience
            this.claims = claims
        }

    private fun validate(
        audience: String?,
        claims: Map<String, String>?,
        enabledClaims: List<Claim>
    ): Pair<ConfigParsingContext, List<BootstrapInvitation>> {
        val ctx = ConfigParsingContext()
        val result = validator.validate(ctx, listOf(properties(audience, claims)), audiencesById, enabledClaims)
        return ctx to result
    }

    @Test
    fun `validate - Refuse a claim restricted to another audience`() {
        val (ctx, result) = validate(
            audience = "default",
            claims = mapOf("custom_tier" to "gold"),
            enabledClaims = listOf(claim("custom_tier", audienceId = "billing"))
        )

        assertTrue(ctx.hasErrors)
        assertEquals(
            listOf("config.bootstrap_invitation.claim_of_another_audience"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("invitations.first-admin.claims.custom_tier"), ctx.errors.map { it.key })
        assertTrue(result.isEmpty())
    }

    @Test
    fun `validate - Accept a claim restricted to the invitation's own audience`() {
        val (ctx, result) = validate(
            audience = "billing",
            claims = mapOf("custom_tier" to "gold"),
            enabledClaims = listOf(claim("custom_tier", audienceId = "billing"))
        )

        assertFalse(ctx.hasErrors)
        assertEquals(mapOf("custom_tier" to "gold"), result.single().claims)
    }

    @Test
    fun `validate - Accept a claim restricted to no audience`() {
        val (ctx, result) = validate(
            audience = "default",
            claims = mapOf("custom_region" to "eu-west"),
            enabledClaims = listOf(claim("custom_region"))
        )

        assertFalse(ctx.hasErrors)
        assertEquals(mapOf("custom_region" to "eu-west"), result.single().claims)
    }

    @Test
    fun `validate - Report the unknown claim rather than its audience`() {
        val (ctx, _) = validate(
            audience = "default",
            claims = mapOf("custom_tier" to "gold"),
            enabledClaims = emptyList()
        )

        assertEquals(
            listOf("config.bootstrap_invitation.unknown_claim"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Ask no audience question of an invitation that named none`() {
        val (ctx, _) = validate(
            audience = null,
            claims = mapOf("custom_tier" to "gold"),
            enabledClaims = listOf(claim("custom_tier", audienceId = "billing"))
        )

        assertEquals(listOf("config.missing"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validate - Match a claim key Micronaut normalized`() {
        val (ctx, result) = validate(
            audience = "default",
            claims = mapOf("is-sympauthy-admin" to "true"),
            enabledClaims = listOf(claim("is_sympauthy_admin"))
        )

        assertFalse(ctx.hasErrors)
        assertEquals(mapOf("is_sympauthy_admin" to "true"), result.single().claims)
    }

    @Test
    fun `validate - Carry no claim when none is configured`() {
        val (ctx, result) = validate(audience = "default", claims = null, enabledClaims = emptyList())

        assertFalse(ctx.hasErrors)
        assertNull(result.single().claims)
    }
}
