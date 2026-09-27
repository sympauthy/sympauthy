package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublication
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
        publishedIn = ClaimPublication.entries.toSet(),
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

    private fun validate(
        parsed: List<ParsedClaim> = emptyList(),
        writtenKeys: Set<String> = emptySet(),
        identifierClaims: List<String>? = null
    ): ConfigParsingContext {
        val ctx = ConfigParsingContext()
        validator.validate(
            ctx, parsed, writtenKeys, emptyMap(), audiencesById, emptyMap<String, Scope>(),
            identifierClaims, null
        )
        return ctx
    }

    @Test
    fun `validate - Refuse a claim named as the word a capability document answers for`() {
        val ctx = validate(listOf(parsedClaim("capabilities")))

        assertEquals(listOf("config.reserved_identifier"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.capabilities"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Refuse an identifier claim restricted to an audience`() {
        val ctx = validate(listOf(parsedClaim("email", audienceId = "billing")), identifierClaims = listOf("email"))

        assertTrue(ctx.hasErrors)
        assertEquals(
            listOf("config.claims.identifier_claim.audience_restricted"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.audience"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Accept an identifier claim restricted to no audience`() {
        val ctx = validate(listOf(parsedClaim("email")), identifierClaims = listOf("email"))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a claim restricted to an audience that identifies nobody`() {
        val ctx = validate(listOf(parsedClaim("nickname", audienceId = "billing")), identifierClaims = listOf("email"))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Refuse a key written on a generated claim, as the file spells it`() {
        val ctx = validate(writtenKeys = setOf("claims.sub.enabled"))

        assertEquals(listOf("config.claim.generated.not_configurable"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.sub.enabled"), ctx.errors.map { it.key })
        assertEquals(listOf("sub"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Refuse a key a claim's configuration does not declare at all`() {
        // Nothing has to be said here for a property added to a claim tomorrow to be refused on a
        // generated one, which is the step `published-in` skipped.
        val ctx = validate(writtenKeys = setOf("claims.sub.collected-at"))

        assertEquals(listOf("claims.sub.collected-at"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Refuse a key written on a generated claim spelt with a hyphen`() {
        val ctx = validate(writtenKeys = setOf("claims.updated-at.type"))

        assertEquals(listOf("claims.updated-at.type"), ctx.errors.map { it.key })
        assertEquals(listOf("updated_at"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Report every key written on a generated claim`() {
        val writtenKeys = setOf(
            "claims.sub.enabled",
            "claims.sub.acl.consent-scope",
            "claims.updated_at.type",
            "claims.email.enabled"
        )

        val ctx = validate(writtenKeys = writtenKeys)

        assertEquals(
            listOf("claims.sub.acl.consent-scope", "claims.sub.enabled", "claims.updated_at.type"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Accept a generated claim the deployment wrote no key under`() {
        val ctx = validate(writtenKeys = setOf("claims.sub", "claims.email.type"))

        assertFalse(ctx.hasErrors)
    }
}
