package com.sympauthy.config.validation

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.DisabledClaim
import com.sympauthy.business.model.user.claim.PersonalClaim
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.EnabledClaimsConfig
import com.sympauthy.config.parsing.ParsedAuthConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthConfigValidatorTest {

    private val validator = AuthConfigValidator()

    private fun claim(id: String, dataType: ClaimDataType, enabled: Boolean = true): Claim =
        if (enabled) {
            PersonalClaim(
                id = id,
                verifiedId = null,
                dataType = dataType,
                group = null,
                required = false,
                allowedValues = null,
                publishedIn = ClaimPublicationPlace.entries.toSet(),
                publishedInWhenRequested = emptySet(),
                acl = acl
            )
        } else {
            DisabledClaim(
                id = id,
                verifiedId = null,
                dataType = dataType,
                kind = ClaimKind.PERSONAL,
                group = null,
                required = false,
                allowedValues = null,
                acl = acl
            )
        }

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByPerson = true,
            collectedInFlow = true,
            writableByPerson = false,
            readableByClient = true,
            writableByClient = false,
            writeMaxAuthenticationAge = null
        ),
        unconditional = UnconditionalAcl(
            readableWithClientScopes = emptyList(),
            writableWithClientScopes = emptyList()
        )
    )

    private fun parsedAuth(
        identifierClaims: List<String>,
        accessExpiration: Duration = Duration.ofMinutes(5),
        idExpiration: Duration? = Duration.ofMinutes(5),
        refreshExpiration: Duration? = null,
        authorizationCodeExpiration: Duration = Duration.ofMinutes(1),
        interactiveFlowExpiration: Duration = Duration.ofMinutes(30),
        grantExpiration: Duration? = null
    ) = ParsedAuthConfig(
        issuer = "https://auth.example.com",
        accessExpiration = accessExpiration,
        idExpiration = idExpiration,
        refreshEnabled = false,
        refreshExpiration = refreshExpiration,
        dpopRequired = false,
        authorizationCodeExpiration = authorizationCodeExpiration,
        interactiveFlowExpiration = interactiveFlowExpiration,
        grantExpiration = grantExpiration,
        identifierClaims = identifierClaims,
        userMergingEnabled = false,
        byPasswordEnabled = true
    )

    private fun validate(identifierClaims: List<String>, vararg claims: Claim): ConfigParsingContext {
        val ctx = ConfigParsingContext()
        validator.validate(ctx, parsedAuth(identifierClaims), EnabledClaimsConfig(claims.toList()))
        return ctx
    }

    @Test
    fun `validate - Refuse every lifetime of zero, under the key it was written at`() {
        val ctx = ConfigParsingContext()

        val validated = validator.validate(
            ctx,
            parsedAuth(
                emptyList(),
                accessExpiration = Duration.ZERO,
                idExpiration = Duration.ZERO,
                refreshExpiration = Duration.ZERO,
                authorizationCodeExpiration = Duration.ZERO,
                interactiveFlowExpiration = Duration.ZERO,
                grantExpiration = Duration.ZERO
            ),
            EnabledClaimsConfig(emptyList())
        )

        assertNull(validated)
        assertEquals(
            listOf(
                "auth.token.access-expiration",
                "auth.token.id-expiration",
                "auth.token.refresh-expiration",
                "auth.authorization-code.expiration",
                "auth.interactive-flow.expiration",
                "auth.grant.expiration"
            ),
            ctx.errors.filter { it.messageId == "config.lifetime.not_positive" }.map { it.key }
        )
    }

    @Test
    fun `validate - Give the id token the lifetime of the access token where none is written`() {
        val ctx = ConfigParsingContext()

        val validated = validator.validate(
            ctx,
            parsedAuth(listOf("email"), accessExpiration = Duration.ofMinutes(7), idExpiration = null),
            EnabledClaimsConfig(listOf(claim("email", ClaimDataType.EMAIL)))
        )

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
        assertEquals(Duration.ofMinutes(7), validated?.token?.idExpiration)
    }

    /** The id token's lifetime defaults to the access token's, so the one key written is the one refused. */
    @Test
    fun `validate - Refuse an access lifetime of zero once, where no id lifetime is written`() {
        val ctx = ConfigParsingContext()

        validator.validate(
            ctx,
            parsedAuth(emptyList(), accessExpiration = Duration.ZERO, idExpiration = null),
            EnabledClaimsConfig(emptyList())
        )

        assertEquals(
            listOf("auth.token.access-expiration"),
            ctx.errors.filter { it.messageId == "config.lifetime.not_positive" }.map { it.key }
        )
    }

    @Test
    fun `validate - Accept a grant lifetime greater than zero`() {
        val ctx = ConfigParsingContext()
        val validated = validator.validate(
            ctx,
            parsedAuth(listOf("email"), grantExpiration = Duration.ofHours(1)),
            EnabledClaimsConfig(listOf(claim("email", ClaimDataType.EMAIL)))
        )

        assertEquals(emptyList<String>(), ctx.errors.map { it.messageId })
        assertEquals(Duration.ofHours(1), validated?.grant?.expiration)
    }

    @Test
    fun `validate - Refuse an identifier claim the claims configuration does not enable`() {
        val ctx = validate(listOf("email"), claim("email", ClaimDataType.EMAIL, enabled = false))

        assertEquals(listOf("config.auth.identifier_claim.disabled"), ctx.errors.map { it.messageId })
    }

    @Test
    fun `validate - Refuse an identifier claim of a type that cannot identify anybody`() {
        val ctx = validate(listOf("birth_date"), claim("birth_date", ClaimDataType.DATE))

        assertEquals(
            listOf("config.auth.identifier_claim.unidentifiable_type"),
            ctx.errors.map { it.messageId }
        )
        assertEquals("date", ctx.errors.single().values["type"])
    }

    @Test
    fun `validate - Refuse an identifier claim naming a property a whole population shares`() {
        val ctx = validate(listOf("zoneinfo"), claim("zoneinfo", ClaimDataType.TIMEZONE))

        assertEquals(
            listOf("config.auth.identifier_claim.unidentifiable_type"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Accept every type somebody is known by and types to sign in`() {
        val identifying = listOf(
            "email" to ClaimDataType.EMAIL,
            "phone_number" to ClaimDataType.PHONE_NUMBER,
            "employee_number" to ClaimDataType.NUMBER,
            "preferred_username" to ClaimDataType.STRING
        )

        val ctx = validate(
            identifying.map { it.first },
            *identifying.map { (id, dataType) -> claim(id, dataType) }.toTypedArray()
        )

        assertFalse(ctx.hasErrors)
    }
}
