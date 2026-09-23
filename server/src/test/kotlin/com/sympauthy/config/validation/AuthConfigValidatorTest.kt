package com.sympauthy.config.validation

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.EnabledClaimsConfig
import com.sympauthy.config.parsing.ParsedAuthConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Duration

class AuthConfigValidatorTest {

    private val validator = AuthConfigValidator()

    private fun claim(id: String, dataType: ClaimDataType, enabled: Boolean = true) = Claim(
        id = id,
        enabled = enabled,
        verifiedId = null,
        dataType = dataType,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
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

    private fun parsedAuth(identifierClaims: List<String>) = ParsedAuthConfig(
        issuer = "https://auth.example.com",
        accessExpiration = Duration.ofMinutes(5),
        idExpiration = Duration.ofMinutes(5),
        refreshEnabled = false,
        refreshExpiration = null,
        dpopRequired = false,
        authorizationCodeExpiration = Duration.ofMinutes(1),
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
