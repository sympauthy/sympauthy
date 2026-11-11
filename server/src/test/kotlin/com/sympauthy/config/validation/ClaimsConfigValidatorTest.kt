package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.ParsedClaimAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        publishedInWhenRequested: Set<ClaimPublicationPlace> = emptySet(),
        kind: ClaimKind? = ClaimKind.PERSONAL,
        collectedInFlow: String? = "true",
        writableByPerson: String? = null,
        writableByClient: String? = "false",
        dataType: ClaimDataType = ClaimDataType.EMAIL,
        group: ClaimGroup? = null,
        required: Boolean = false,
        allowedValues: List<Any>? = null,
        maxSize: Int? = null
    ) = ParsedClaim(
        id = id,
        enabled = true,
        dataType = dataType,
        group = group,
        required = required,
        generated = false,
        verifiedId = verifiedId,
        kind = kind,
        audienceId = audienceId,
        allowedValues = allowedValues,
        maxSize = maxSize,
        publishedIn = publishedIn,
        publishedInWhenRequested = publishedInWhenRequested,
        acl = ParsedClaimAcl(
            collectedInFlow = collectedInFlow,
            readableByPerson = "true",
            writableByPerson = writableByPerson,
            readableByClient = "true",
            writableByClient = writableByClient
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
    fun `validate - Accept a claim advertised beside a channel it opens to a request`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.DISCOVERY),
                    publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
                )
            )
        )

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Refuse a claim advertised beside an on-request place no request can name`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.DISCOVERY),
                    publishedInWhenRequested = setOf(ClaimPublicationPlace.ACCESS_TOKEN)
                )
            )
        )

        assertTrue(
            "config.claim.published_in.advertised_without_a_carrier" in ctx.errors.map { it.messageId },
            "a place no request can name carries nothing, so the claim is advertised without a carrier"
        )
    }

    @Test
    fun `validate - Refuse a channel a claim names in both of its publication lists`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.ID_TOKEN),
                    publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
                )
            )
        )

        assertEquals(
            listOf("config.claim.published_in_when_requested.already_published"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.published-in-when-requested"), ctx.errors.map { it.key })
        assertEquals(listOf("email"), ctx.errors.map { it.values["claim"] })
        assertEquals(listOf("id-token"), ctx.errors.map { it.values["place"] })
    }

    @Test
    fun `validate - Refuse every channel a claim names in both of its publication lists`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.ID_TOKEN, ClaimPublicationPlace.USERINFO),
                    publishedInWhenRequested = setOf(
                        ClaimPublicationPlace.ID_TOKEN, ClaimPublicationPlace.USERINFO
                    )
                )
            )
        )

        assertEquals(listOf("id-token", "userinfo"), ctx.errors.map { it.values["place"] })
    }

    @Test
    fun `validate - Refuse a place a claim opens to a request that no request can name`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.ID_TOKEN),
                    publishedInWhenRequested = setOf(ClaimPublicationPlace.ACCESS_TOKEN)
                )
            )
        )

        assertEquals(
            listOf("config.claim.published_in_when_requested.not_a_channel"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.email.published-in-when-requested"), ctx.errors.map { it.key })
        assertEquals(listOf("access-token"), ctx.errors.map { it.values["place"] })
    }

    @Test
    fun `validate - Accept a claim opening an OpenID channel to a request`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "email",
                    publishedIn = setOf(ClaimPublicationPlace.USERINFO),
                    publishedInWhenRequested = setOf(ClaimPublicationPlace.ID_TOKEN)
                )
            )
        )

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
        val ctx = validate(listOf(parsedClaim("nickname", writableByClient = "true")))

        assertEquals(
            listOf("config.claim.kind.shared_personal_claim_client_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.nickname.acl.writable-by-client"),
            ctx.errors.map { it.key }
        )
        assertEquals(listOf("nickname"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Refuse any expression opening the client write of a personal claim restricted to no audience`() {
        val ctx = validate(listOf(parsedClaim("nickname", writableByClient = """CLIENT("id") = "billing"""")))

        assertEquals(
            listOf("config.claim.kind.shared_personal_claim_client_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(listOf("claims.nickname.acl.writable-by-client"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Accept a client write of a personal claim restricted to one audience`() {
        val ctx = validate(
            listOf(parsedClaim("nickname", audienceId = "billing", writableByClient = "true"))
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
                    collectedInFlow = null,
                    writableByClient = "true"
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
            listOf("claims.credit_score.acl.collected-in-flow"),
            ctx.errors.map { it.key }
        )
        assertEquals(listOf("credit_score"), ctx.errors.map { it.values["claim"] })
    }

    @Test
    fun `validate - Accept an application claim shutting the flow's door with the literal false`() {
        val ctx = validate(listOf(parsedClaim("credit_score", kind = ClaimKind.APPLICATION, collectedInFlow = "false")))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Refuse a person's own token writing an application claim`() {
        val ctx = validate(
            listOf(
                parsedClaim(
                    "credit_score",
                    kind = ClaimKind.APPLICATION,
                    collectedInFlow = null,
                    writableByPerson = "true"
                )
            )
        )

        assertEquals(
            listOf("config.claim.kind.application_claim_person_write"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.credit_score.acl.writable-by-person"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Refuse an identifier claim declared an application's`() {
        val ctx = validate(
            listOf(parsedClaim("email", kind = ClaimKind.APPLICATION, collectedInFlow = null)),
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
            listOf(parsedClaim("credit_score", kind = ClaimKind.APPLICATION, collectedInFlow = null)),
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
    fun `validate - Refuse a required claim whose flow door is shut`() = assertRefused(
        parsedClaim("nickname", required = true, collectedInFlow = "false"),
        "claims.nickname.required",
        "config.claim.required.never_collected"
    )

    @Test
    fun `validate - Refuse a required claim whose flow door is written nowhere`() = assertRefused(
        parsedClaim("nickname", required = true, collectedInFlow = null),
        "claims.nickname.required",
        "config.claim.required.never_collected"
    )

    @Test
    fun `validate - Refuse a required application claim`() = assertRefused(
        parsedClaim("credit_score", kind = ClaimKind.APPLICATION, required = true, collectedInFlow = null),
        "claims.credit_score.required",
        "config.claim.required.never_collected"
    )

    @Test
    fun `validate - Accept a required claim the flow collects`() {
        val ctx = validate(listOf(parsedClaim("nickname", required = true)))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a required identifier claim whose flow door is shut`() {
        val ctx = validate(
            listOf(parsedClaim("email", required = true, collectedInFlow = null)),
            identifierClaims = listOf("email")
        )

        assertFalse(ctx.hasErrors)
    }

    /** A JSON claim the flow cannot reach, so that each case below refuses the one key it is about. */
    private fun parsedJsonClaim(
        required: Boolean = false,
        collectedInFlow: String? = null,
        group: ClaimGroup? = null,
        verifiedId: String? = null,
        allowedValues: List<Any>? = null,
        maxSize: Int? = null
    ) = parsedClaim(
        "preferences",
        dataType = ClaimDataType.JSON,
        required = required,
        collectedInFlow = collectedInFlow,
        group = group,
        verifiedId = verifiedId,
        allowedValues = allowedValues,
        maxSize = maxSize,
        writableByPerson = "true"
    )

    private fun assertRefused(parsed: ParsedClaim, key: String, messageId: String) {
        val ctx = validate(listOf(parsed))

        assertEquals(listOf(messageId), ctx.errors.map { it.messageId })
        assertEquals(listOf(key), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Accept a JSON claim written through the APIs alone`() {
        val ctx = validate(listOf(parsedJsonClaim()))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Refuse a JSON claim declared required`() = assertRefused(
        parsedJsonClaim(required = true), "claims.preferences.required", "config.claim.type.required"
    )

    @Test
    fun `validate - Refuse the flow collecting a JSON claim`() = assertRefused(
        parsedJsonClaim(collectedInFlow = "true"),
        "claims.preferences.acl.collected-in-flow",
        "config.claim.type.collected_in_flow"
    )

    @Test
    fun `validate - Refuse a JSON claim placed in a group`() = assertRefused(
        parsedJsonClaim(group = ClaimGroup.IDENTITY), "claims.preferences.group", "config.claim.type.group"
    )

    @Test
    fun `validate - Refuse a JSON claim naming a verified companion`() = assertRefused(
        parsedJsonClaim(verifiedId = "preferences_verified"),
        "claims.preferences.verified-id",
        "config.claim.type.verified_id"
    )

    @Test
    fun `validate - Refuse a JSON claim restricted to allowed values`() = assertRefused(
        parsedJsonClaim(allowedValues = listOf(mapOf("theme" to "dark"))),
        "claims.preferences.allowed-values",
        "config.claim.type.allowed_values"
    )

    @Test
    fun `validate - Refuse a max-size on a claim whose type holds values to none`() = assertRefused(
        parsedClaim("email", maxSize = 1024), "claims.email.max-size", "config.claim.type.max_size"
    )

    @Test
    fun `validate - Refuse a max-size that is not greater than zero`() = assertRefused(
        parsedJsonClaim(maxSize = 0), "claims.preferences.max-size", "config.claim.max_size.not_positive"
    )

    @Test
    fun `validate - Hold a JSON claim to its type's size where it declares none`() {
        val claim = validateClaims(listOf(parsedJsonClaim())).single { it.id == "preferences" }

        assertEquals(ClaimDataType.JSON.defaultMaxSize, claim.maxSize)
    }

    @Test
    fun `validate - Hold a JSON claim to the size it declares`() {
        val claim = validateClaims(listOf(parsedJsonClaim(maxSize = 65_536))).single { it.id == "preferences" }

        assertEquals(65_536, claim.maxSize)
    }

    @Test
    fun `validate - Hold a claim whose type holds values to no size to none`() {
        val claim = validateClaims(listOf(parsedClaim("email"))).single { it.id == "email" }

        assertNull(claim.maxSize)
    }

    private fun validateClaims(parsed: List<ParsedClaim>): List<Claim> = validator.validate(
        ConfigParsingContext(), parsed, emptySet(), emptyMap(), audiencesById, emptyMap<String, Scope>(), null, null
    )

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
            "claims.sub.acl.readable-by-client",
            "claims.updated_at.type",
            "claims.email.enabled"
        )

        val ctx = validate(writtenKeys = writtenKeys)

        assertEquals(
            listOf("claims.sub.acl.readable-by-client", "claims.sub.enabled", "claims.updated_at.type"),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Accept a generated claim the deployment wrote no key under`() {
        val ctx = validate(writtenKeys = setOf("claims.sub", "claims.email.type"))

        assertFalse(ctx.hasErrors)
    }
}
