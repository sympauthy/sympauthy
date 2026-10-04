package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.WrittenConfigurationKeys
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ClaimAclParser
import com.sympauthy.config.parsing.ClaimsConfigParser
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.validation.ClaimAclValidator
import com.sympauthy.config.validation.ClaimsConfigValidator
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimsConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    @MockK
    lateinit var authProperties: AuthConfigurationProperties

    @MockK
    lateinit var writtenConfigurationKeys: WrittenConfigurationKeys

    lateinit var factory: ClaimsConfigFactory

    private val emptyTemplateAcl = ClaimTemplateAcl(null, null, null, null, null, null, null, null, null)

    private fun applicationTemplate() = ClaimTemplate(
        id = APPLICATION,
        enabled = null,
        required = null,
        group = null,
        kind = ClaimKind.APPLICATION,
        audienceId = null,
        allowedValues = null,
        publishedIn = null,
        publishedInWhenRequested = null,
        acl = emptyTemplateAcl
    )

    private fun personalTemplate() = ClaimTemplate(
        id = PERSONAL,
        enabled = false,
        required = null,
        group = null,
        kind = ClaimKind.PERSONAL,
        audienceId = null,
        allowedValues = null,
        publishedIn = null,
        publishedInWhenRequested = null,
        acl = emptyTemplateAcl
    )

    private fun factoryWithTemplate(template: ClaimTemplate) = ClaimsConfigFactory(
        ClaimsConfigParser(parser, ClaimAclParser(parser)),
        ClaimsConfigValidator(ClaimAclValidator()),
        authProperties,
        writtenConfigurationKeys,
        EnabledClaimTemplatesConfig(mapOf(template.id to template)),
        EnabledAudiencesConfig(emptyList()),
        EnabledScopesConfig(emptyList())
    )

    @BeforeEach
    fun setUp() {
        every { authProperties.userMergingEnabled } returns null
        every { authProperties.identifierClaims } returns null
        every { writtenConfigurationKeys.under(any()) } returns emptySet()

        val claimAclParser = ClaimAclParser(parser)
        val claimAclValidator = ClaimAclValidator()

        val templates = mapOf(
            APPLICATION to applicationTemplate(),
            PERSONAL to personalTemplate()
        )
        val claimTemplatesConfig = EnabledClaimTemplatesConfig(templates)
        factory = ClaimsConfigFactory(
            ClaimsConfigParser(parser, claimAclParser),
            ClaimsConfigValidator(claimAclValidator),
            authProperties,
            writtenConfigurationKeys,
            claimTemplatesConfig,
            EnabledAudiencesConfig(emptyList()),
            EnabledScopesConfig(emptyList())
        )
    }

    private fun claimProperties(
        id: String,
        type: String? = null,
        enabled: String? = null,
        required: String? = null,
        template: String? = APPLICATION,
        group: String? = null,
        verifiedId: String? = null,
        kind: String? = null,
        collectedInFlow: String? = null,
        clientWriteScopes: List<String>? = null
    ): ClaimConfigurationProperties {
        return ClaimConfigurationProperties(id).apply {
            this.type = type
            this.template = template
            this.group = group
            this.verifiedId = verifiedId
            this.kind = kind
            this.acl = if (collectedInFlow == null && clientWriteScopes == null) null else {
                object : ClaimConfigurationProperties.AclConfig {
                    override val consentScope = null
                    override val readableByPersonWhenConsented = null
                    override val collectedInFlowWhenConsented = collectedInFlow
                    override val writableByPersonWhenConsented = null
                    override val readableByClientWhenConsented = null
                    override val writableByClientWhenConsented = null
                    override val readableWithClientScopesUnconditionally = null
                    override val writableWithClientScopesUnconditionally = clientWriteScopes
                    override val writeMaxAuthenticationAge = null
                }
            }
        }.also {
            if (enabled != null) {
                val field = ClaimConfigurationProperties::class.java.getDeclaredField("enabled")
                field.isAccessible = true
                field.set(it, enabled)
            }
            if (required != null) {
                val field = ClaimConfigurationProperties::class.java.getDeclaredField("required")
                field.isAccessible = true
                field.set(it, required)
            }
        }
    }

    @Test
    fun `provideClaims - Refuse a claim naming no template`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string", template = null)
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        assertEquals(
            listOf("config.claim.kind.missing"),
            (result as DisabledClaimsConfig).configurationErrors
                ?.filterIsInstance<ConfigurationException>()
                ?.map { it.messageId }
        )
    }

    @Test
    fun `provideClaims - Explicit template applied`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = PERSONAL)
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims
        val emailClaim = claims.first { it.id == "email" }
        assertFalse(emailClaim.enabled)
    }

    @Test
    fun `provideClaims - Make a claim naming the application template an application's`() {
        val properties = listOf(claimProperties(id = "credit_score", type = "number"))

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "credit_score" }
        assertEquals(ClaimKind.APPLICATION, claim.kind)
        assertEquals(ClaimOrigin.CUSTOM, claim.origin)
    }

    @Test
    fun `provideClaims - Make a claim naming the personal template the person's`() {
        val properties = listOf(claimProperties(id = "name", type = "string", template = PERSONAL))

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "name" }
        assertEquals(ClaimKind.PERSONAL, claim.kind)
        assertEquals(ClaimOrigin.OPENID_CONNECT, claim.origin)
    }

    @Test
    fun `provideClaims - Refuse the flow collecting a claim on the application template`() {
        val properties = listOf(
            claimProperties(id = "discord_id", type = "string", collectedInFlow = "true")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        assertEquals(
            listOf("config.claim.kind.application_claim_person_write"),
            (result as DisabledClaimsConfig).configurationErrors
                ?.filterIsInstance<ConfigurationException>()
                ?.map { it.messageId }
        )
    }

    @Test
    fun `provideClaims - Refuse an application claim for the flow collection it takes from its template`() {
        val factoryCollectingInTheFlow = factoryWithTemplate(
            personalTemplate().copy(acl = emptyTemplateAcl.copy(collectedInFlowWhenConsented = true))
        )
        val properties = listOf(
            claimProperties(id = "credit_score", type = "number", template = PERSONAL, kind = "application")
        )

        val result = factoryCollectingInTheFlow.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        val errors = (result as DisabledClaimsConfig).configurationErrors!!
            .filterIsInstance<ConfigurationException>()
        assertEquals(
            listOf("config.claim.kind.application_claim_person_write"),
            errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.credit_score.acl.collected-in-flow-when-consented"),
            errors.map { it.key }
        )
    }

    @Test
    fun `provideClaims - Collect a claim the deployment declared the person's`() {
        val properties = listOf(
            claimProperties(id = "discord_id", type = "string", kind = "personal", collectedInFlow = "true")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "discord_id" }
        assertEquals(ClaimKind.PERSONAL, claim.kind)
        assertTrue(claim.collectedInFlow)
    }

    @Test
    fun `provideClaims - Error when referencing nonexistent template`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string", template = "nonexistent")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
    }

    @Test
    fun `provideClaims - Claim fields override template`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = PERSONAL, enabled = "true")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val emailClaim = (result as EnabledClaimsConfig).claims.first { it.id == "email" }
        assertTrue(emailClaim.enabled)
    }

    @Test
    fun `provideClaims - type is required`() {
        val properties = listOf(
            claimProperties(id = "department")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
    }

    @Test
    fun `provideClaims - group parsed from config`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string", group = "identity")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertEquals(ClaimGroup.IDENTITY, claim.group)
    }

    @Test
    fun `provideClaims - verifiedId set from config`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = PERSONAL, verifiedId = "email_verified")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val emailClaim = (result as EnabledClaimsConfig).claims.first { it.id == "email" }
        assertEquals("email_verified", emailClaim.verifiedId)
    }

    @Test
    fun `provideClaims - Generated claims are always present and enabled`() {
        val result = factory.provideClaims(emptyList())

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims

        GeneratedOpenIdConnectClaim.entries.forEach { generated ->
            val claim = claims.firstOrNull { it.id == generated.id }
            assertNotNull(claim, "Generated claim ${generated.id} should be present")
            assertTrue(claim!!.enabled, "Generated claim ${generated.id} should be enabled")
            assertTrue(claim.generated, "Generated claim ${generated.id} should be marked as generated")
            assertFalse(claim.collectedInFlow, "Generated claim ${generated.id} should not be user-inputted")
        }
    }

    @Test
    fun `provideClaims - Generated claims are not duplicated when also in properties`() {
        val properties = listOf(
            claimProperties(id = "sub")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims
        val subClaims = claims.filter { it.id == "sub" }
        assertEquals(1, subClaims.size)
    }

    @Test
    fun `provideClaims - Refuse a key written on a generated claim`() {
        every { writtenConfigurationKeys.under(any()) } returns setOf("claims.sub.type")
        val properties = listOf(
            claimProperties(id = "sub", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        assertEquals(
            listOf("claims.sub.type"),
            (result as DisabledClaimsConfig).configurationErrors
                ?.filterIsInstance<ConfigurationException>()
                ?.map { it.key }
        )
    }

    @Test
    fun `provideClaims - Refuse a template named on a generated claim rather than resolving it`() {
        every { writtenConfigurationKeys.under(any()) } returns setOf("claims.sub.template")
        val properties = listOf(
            claimProperties(id = "sub", template = "nonexistent")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        assertEquals(
            listOf("config.claim.generated.not_configurable"),
            (result as DisabledClaimsConfig).configurationErrors
                ?.filterIsInstance<ConfigurationException>()
                ?.map { it.messageId }
        )
    }

    @Test
    fun `provideClaims - Refuse a claim whose template says whose the value is no more than it does`() {
        val factoryWithASilentTemplate = factoryWithTemplate(applicationTemplate().copy(kind = null))
        val properties = listOf(claimProperties(id = "credit_score", type = "number"))

        val result = factoryWithASilentTemplate.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        val errors = (result as DisabledClaimsConfig).configurationErrors!!
            .filterIsInstance<ConfigurationException>()
        assertEquals(listOf("config.claim.kind.missing"), errors.map { it.messageId })
        assertEquals(listOf("claims.credit_score.kind"), errors.map { it.key })
    }

    @Test
    fun `provideClaims - Refuse a personal claim for the client write it takes from its template`() {
        val factoryGrantingTheClientWrite = factoryWithTemplate(
            applicationTemplate().copy(
                acl = emptyTemplateAcl.copy(
                    writableWithClientScopesUnconditionally = listOf("users:claims:write")
                )
            )
        )
        val properties = listOf(claimProperties(id = "discord_id", type = "string", kind = "personal"))

        val result = factoryGrantingTheClientWrite.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
        val errors = (result as DisabledClaimsConfig).configurationErrors!!
            .filterIsInstance<ConfigurationException>()
        assertEquals(
            listOf("config.claim.kind.shared_personal_claim_client_write"),
            errors.map { it.messageId }
        )
        assertEquals(
            listOf("claims.discord_id.acl.writable-with-client-scopes-unconditionally"),
            errors.map { it.key }
        )
    }

    @Test
    fun `provideClaims - Accept a personal claim that answers the client write with an empty list`() {
        val factoryGrantingTheClientWrite = factoryWithTemplate(
            applicationTemplate().copy(
                acl = emptyTemplateAcl.copy(
                    writableWithClientScopesUnconditionally = listOf("users:claims:write")
                )
            )
        )
        val properties = listOf(
            claimProperties(id = "discord_id", type = "string", kind = "personal", clientWriteScopes = emptyList())
        )

        val result = factoryGrantingTheClientWrite.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "discord_id" }
        assertEquals(ClaimKind.PERSONAL, claim.kind)
        assertEquals(emptyList<String>(), claim.acl.unconditional.writableWithClientScopes)
    }

    @Test
    fun `provideClaims - collectedInFlow is true when ACL allows user write`() {
        val writableFactory = factoryWithTemplate(
            applicationTemplate().copy(
                kind = ClaimKind.PERSONAL,
                acl = emptyTemplateAcl.copy(collectedInFlowWhenConsented = true)
            )
        )

        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = writableFactory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertTrue(claim.collectedInFlow)
    }

    @Test
    fun `provideClaims - collectedInFlow is false when ACL disallows user write`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertFalse(claim.collectedInFlow)
    }

    @Test
    fun `provideClaims - OpenID claim has OPENID_CONNECT origin`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = PERSONAL, enabled = "true")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val emailClaim = (result as EnabledClaimsConfig).claims.first { it.id == "email" }
        assertEquals(ClaimOrigin.OPENID_CONNECT, emailClaim.origin)
    }

    @Test
    fun `provideClaims - Custom claim has CUSTOM origin`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertEquals(ClaimOrigin.CUSTOM, claim.origin)
    }
}

private const val APPLICATION = "application"

private const val PERSONAL = "personal"
