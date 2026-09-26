package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimPublication
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.ParsedClaimAcl
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.validation.ClaimsConfigValidator.Companion.KEYS_NO_GENERATED_CLAIM_READS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource

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
        propertiesList: List<ClaimConfigurationProperties> = emptyList(),
        identifierClaims: List<String>? = null
    ): ConfigParsingContext {
        val ctx = ConfigParsingContext()
        validator.validate(
            ctx, parsed, propertiesList, emptyMap(), audiencesById, emptyMap<String, Scope>(),
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

    @ParameterizedTest
    @FieldSource("keysNoGeneratedClaimReads")
    fun `validate - Refuse a key written on a generated claim against its own name`(written: WrittenKey) {
        val ctx = validate(propertiesList = listOf(written.properties))

        assertEquals(listOf("config.claim.generated.not_configurable"), ctx.errors.map { it.messageId })
        assertEquals(listOf("claims.sub.${written.key}"), ctx.errors.map { it.key })
    }

    @Test
    fun `Every key a generated claim does not read has a case above`() {
        assertEquals(
            KEYS_NO_GENERATED_CLAIM_READS.keys,
            keysNoGeneratedClaimReads.mapTo(mutableSetOf(), WrittenKey::key),
            "The keys refused above and the keys a case is written for have to be the same set: a key " +
                "with no case of its own is one whose refusal names whatever property the set reads for it."
        )
    }

    @Test
    fun `validate - Refuse a key written on a generated claim spelt with an underscore`() {
        val properties = ClaimConfigurationProperties("updated-at").apply { type = "string" }

        val ctx = validate(propertiesList = listOf(properties))

        assertEquals(listOf("claims.updated_at.type"), ctx.errors.map { it.key })
    }

    @Test
    fun `validate - Report every key written on a generated claim`() {
        val propertiesList = listOf(
            ClaimConfigurationProperties("sub").apply {
                enabled = "false"
                publishedIn = listOf("userinfo")
            },
            ClaimConfigurationProperties("updated-at").apply {
                type = "string"
                acl = aclProperties(consentScope = "profile")
            }
        )

        val ctx = validate(propertiesList = propertiesList)

        assertEquals(
            listOf(
                "claims.sub.enabled",
                "claims.sub.published-in",
                "claims.updated_at.type",
                "claims.updated_at.acl.consent-scope"
            ),
            ctx.errors.map { it.key }
        )
    }

    @Test
    fun `validate - Accept a generated claim the deployment wrote no key under`() {
        val ctx = validate(propertiesList = listOf(ClaimConfigurationProperties("sub")))

        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `validate - Accept a key written on a claim the deployment configures`() {
        val properties = ClaimConfigurationProperties("email").apply { enabled = "false" }

        val ctx = validate(propertiesList = listOf(properties))

        assertFalse(ctx.hasErrors)
    }

    companion object {

        @JvmStatic
        private val keysNoGeneratedClaimReads = listOf(
            WrittenKey("template") { template = "openid" },
            WrittenKey("enabled") { enabled = "false" },
            WrittenKey("required") { required = "true" },
            WrittenKey("type") { type = "string" },
            WrittenKey("group") { group = "identity" },
            WrittenKey("verified-id") { verifiedId = "sub_verified" },
            WrittenKey("allowed-values") { allowedValues = listOf("anonymous") },
            WrittenKey("audience") { audience = "billing" },
            WrittenKey("published-in") { publishedIn = listOf("userinfo") },
            WrittenKey("acl.consent-scope") { acl = aclProperties(consentScope = "profile") },
            WrittenKey("acl.readable-by-user-when-consented") {
                acl = aclProperties(readableByUser = "true")
            },
            WrittenKey("acl.writable-by-user-when-consented") {
                acl = aclProperties(writableByUser = "true")
            },
            WrittenKey("acl.readable-by-client-when-consented") {
                acl = aclProperties(readableByClient = "true")
            },
            WrittenKey("acl.writable-by-client-when-consented") {
                acl = aclProperties(writableByClient = "true")
            },
            WrittenKey("acl.readable-with-client-scopes-unconditionally") {
                acl = aclProperties(readableWithClientScopes = listOf("users:claims:read"))
            },
            WrittenKey("acl.writable-with-client-scopes-unconditionally") {
                acl = aclProperties(writableWithClientScopes = listOf("users:claims:write"))
            }
        )
    }
}

/**
 * One key written under `claims.sub` and nothing else, so an error naming another key is the set of
 * refused keys reading the wrong property for this one.
 */
class WrittenKey(
    val key: String,
    write: ClaimConfigurationProperties.() -> Unit
) {
    val properties = ClaimConfigurationProperties("sub").apply(write)

    override fun toString() = key
}

private fun aclProperties(
    consentScope: String? = null,
    readableByUser: String? = null,
    writableByUser: String? = null,
    readableByClient: String? = null,
    writableByClient: String? = null,
    readableWithClientScopes: List<String>? = null,
    writableWithClientScopes: List<String>? = null
): ClaimConfigurationProperties.AclConfig = object : ClaimConfigurationProperties.AclConfig {
    override val consentScope = consentScope
    override val readableByUserWhenConsented = readableByUser
    override val writableByUserWhenConsented = writableByUser
    override val readableByClientWhenConsented = readableByClient
    override val writableByClientWhenConsented = writableByClient
    override val readableWithClientScopesUnconditionally = readableWithClientScopes
    override val writableWithClientScopesUnconditionally = writableWithClientScopes
}
