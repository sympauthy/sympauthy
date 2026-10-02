package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
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

    private fun parsedClaim(
        id: String,
        audienceId: String? = null,
        verifiedId: String? = null,
        publishedIn: Set<ClaimPublicationPlace> = ClaimPublicationPlace.entries.toSet(),
        kind: ClaimKind? = ClaimKind.PERSONAL,
        collectedInFlow: Boolean? = true,
        writableByPerson: Boolean? = null,
        writableByClient: Boolean? = false,
        writableWithClientScopes: List<String> = emptyList()
    ) = ParsedClaim(
        id = id,
        enabled = true,
        dataType = ClaimDataType.EMAIL,
        group = null,
        required = false,
        generated = false,
        verifiedId = verifiedId,
        kind = kind,
        audienceId = audienceId,
        allowedValues = null,
        publishedIn = publishedIn,
        acl = ParsedClaimAcl(
            consentScope = null,
            readableByPerson = true,
            collectedInFlow = collectedInFlow,
            writableByPerson = writableByPerson,
            readableByClient = true,
            writableByClient = writableByClient,
            readableWithClientScopes = emptyList(),
            writableWithClientScopes = writableWithClientScopes,
            writeMaxAuthenticationAge = null
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
    fun `validate - Refuse a claim advertised in the discovery document and carried nowhere`() {
        val ctx = validate(listOf(parsedClaim("email", publishedIn = setOf(ClaimPublicationPlace.DISCOVERY))))

        assertEquals(
            listOf("config.claim.published_in.advertised_without_a_carrier"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.published-in"), ctx.errors.map { it.key })
        assertEquals(listOf("email"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Accept a claim advertised beside a place that carries its value`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.INTROSPECTION, ClaimPublicationPlace.DISCOVERY)
                )
            )
        )

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a claim carried and not advertised`() {
        val ctx = validate(listOf(parsedClaim("email", publishedIn = setOf(ClaimPublicationPlace.ID_TOKEN))))

        assertFalse(ctx.hasErrors)
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
    fun `validate - Refuse a client write of a personal claim restricted to no audience`() {
        val ctx = validate(listOf(parsedClaim("nickname", writableByClient = true)))

        assertEquals(
            listOf("config.claim.kind.shared_personal_claim_client_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.nickname.acl.writable-by-client-when-consented"),
            ctx.errors.map { it.key }
        )
        assertEquals(listOf("nickname"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Refuse a client scope writing a personal claim restricted to no audience`() {
        val ctx = validate(
            listOf(parsedClaim("nickname", writableWithClientScopes = listOf("users:claims:write")))
        )

        assertEquals(
            listOf("config.claim.kind.shared_personal_claim_client_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.nickname.acl.writable-with-client-scopes-unconditionally"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Name each key granting a client the write of a shared personal claim`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "nickname",
                    writableByClient = true,
                    writableWithClientScopes = listOf("users:claims:write")
                )
            )
        )

        assertEquals(
            listOf(
                "claims.nickname.acl.writable-by-client-when-consented",
                "claims.nickname.acl.writable-with-client-scopes-unconditionally"
            ),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Accept a client write of a personal claim restricted to one audience`() {
        val ctx = validate(
            listOf(parsedClaim("nickname", audienceId = "billing", writableByClient = true))
        )

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a client write of an application claim restricted to no audience`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "credit_score",
                    kind = ClaimKind.APPLICATION,
                    collectedInFlow = false,
                    writableByClient = true,
                    writableWithClientScopes = listOf("users:claims:write")
                )
            )
        )

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Refuse the flow collecting an application claim`() {
        val ctx = validate(listOf(parsedClaim("credit_score", kind = ClaimKind.APPLICATION)))

        assertEquals(
            listOf("config.claim.kind.application_claim_person_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.credit_score.acl.collected-in-flow-when-consented"),
            ctx.errors.map { it.key }
        )
        assertEquals(listOf("credit_score"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Refuse a person's own token writing an application claim`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "credit_score",
                    kind = ClaimKind.APPLICATION,
                    collectedInFlow = false,
                    writableByPerson = true
                )
            )
        )

        assertEquals(
            listOf("config.claim.kind.application_claim_person_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.credit_score.acl.writable-by-person-when-consented"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Refuse an identifier claim declared an application's`() {
        val ctx = validate(
            listOf(parsedClaim("email", kind = ClaimKind.APPLICATION, collectedInFlow = false)),
            identifierClaims = listOf("email")
        )

        assertEquals(
            listOf("config.claim.kind.identifier_claim_not_personal"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.kind"), ctx.errors.map { it.key })
        assertEquals(listOf("email"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Accept an application claim no deployment signs people in with`() {
        val ctx = validate(
            listOf(parsedClaim("credit_score", kind = ClaimKind.APPLICATION, collectedInFlow = false)),
            identifierClaims = listOf("email")
        )

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Hold no kind against a claim this server answers for itself`() {
        val generated = parsedClaim("sub", kind = null, collectedInFlow = null).copy(generated = true)

        val ctx = validate(listOf(generated), identifierClaims = listOf("sub"))

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
