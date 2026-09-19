package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.ParsedClaimAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaimsConfigValidatorTest {

    private val validator = ClaimsConfigValidator(ClaimAclValidator())

    private val audiencesById = mapOf(
        "billing" to Audience(id = "billing", tokenAudience = "billing")
    )

    private fun parsedClaim(id: String, audienceId: String? = null) = ParsedClaim(
        id = id,
        enabled = true,
        dataType = ClaimDataType.EMAIL,
        group = null,
        required = false,
        generated = false,
        verifiedId = null,
        audienceId = audienceId,
        allowedValues = null,
        acl = ParsedClaimAcl(
            consentScope = null,
            readableByUser = true,
            writableByUser = true,
            readableByClient = true,
            writableByClient = false,
            readableWithClientScopes = emptyList(),
            writableWithClientScopes = emptyList()
        )
    )

    private fun validate(parsed: List<ParsedClaim>, identifierClaims: List<String>?): ConfigParsingContext {
        val ctx = ConfigParsingContext()
        validator.validate(
            ctx, parsed, emptyMap(), audiencesById, emptyMap<String, Scope>(), identifierClaims, null
        )
        return ctx
    }

    @Test
    fun `validate - Refuse a claim named as the word a capability document answers for`() {
        val ctx = validate(listOf(parsedClaim("capabilities")), null)

        assertEquals(listOf("config.reserved_identifier"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.capabilities"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Refuse an identifier claim restricted to an audience`() {
        val ctx = validate(listOf(parsedClaim("email", audienceId = "billing")), listOf("email"))

        assertTrue(ctx.hasErrors)
        assertEquals(
            listOf("config.claims.identifier_claim.audience_restricted"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.audience"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Accept an identifier claim restricted to no audience`() {
        val ctx = validate(listOf(parsedClaim("email")), listOf("email"))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a claim restricted to an audience that identifies nobody`() {
        val ctx = validate(listOf(parsedClaim("nickname", audienceId = "billing")), listOf("email"))

        assertFalse(ctx.hasErrors)
    }
}
