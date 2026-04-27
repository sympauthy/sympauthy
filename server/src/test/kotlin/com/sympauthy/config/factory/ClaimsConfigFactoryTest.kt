package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ClaimsConfigParser
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
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
    lateinit var claimAclFactory: ClaimAclFactory

    lateinit var factory: ClaimsConfigFactory

    private val defaultAcl = com.sympauthy.business.model.user.claim.ClaimAcl(
        consent = com.sympauthy.business.model.user.claim.ConsentAcl(
            scope = null,
            readableByUser = false,
            writableByUser = false,
            readableByClient = false,
            writableByClient = false
        ),
        unconditional = com.sympauthy.business.model.user.claim.UnconditionalAcl(
            readableWithClientScopes = emptyList(),
            writableWithClientScopes = emptyList()
        )
    )

    private val defaultTemplateAcl = ClaimTemplateAcl(null, null, null, null, null, null, null)

    private fun defaultTemplate() = ClaimTemplate(
        id = DEFAULT,
        enabled = null,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = null,
        acl = defaultTemplateAcl
    )

    private fun openidTemplate() = ClaimTemplate(
        id = "openid",
        enabled = false,
        required = null,
        group = null,
        audienceId = null,
        allowedValues = null,
        acl = defaultTemplateAcl
    )

    @BeforeEach
    fun setUp() {
        every { authProperties.userMergingEnabled } returns null
        every { authProperties.identifierClaims } returns null
        every {
            claimAclFactory.buildAcl(any(), any(), any(), any(), any())
        } returns defaultAcl
        every {
            claimAclFactory.buildGeneratedClaimAcl(any(), any(), any(), any(), any())
        } returns defaultAcl

        val templates = mapOf(
            DEFAULT to defaultTemplate(),
            "openid" to openidTemplate()
        )
        val claimTemplatesConfig = EnabledClaimTemplatesConfig(templates)
        factory = ClaimsConfigFactory(
            ClaimsConfigParser(parser),
            ClaimsConfigValidator(claimAclFactory),
            authProperties,
            claimTemplatesConfig,
            EnabledAudiencesConfig(emptyList())
        )
    }

    private fun claimProperties(
        id: String,
        type: String? = null,
        enabled: String? = null,
        required: String? = null,
        template: String? = null,
        group: String? = null,
        verifiedId: String? = null
    ): ClaimConfigurationProperties {
        return ClaimConfigurationProperties(id).apply {
            this.type = type
            this.template = template
            this.group = group
            this.verifiedId = verifiedId
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

    // region Template resolution

    @Test
    fun `provideClaims - Default template applied when no template set`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims
        val customClaim = claims.first { it.id == "department" }
        // Default template has enabled=null so default is true for custom claims
        assertTrue(customClaim.enabled)
    }

    @Test
    fun `provideClaims - Explicit template applied`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = "openid")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims
        val emailClaim = claims.first { it.id == "email" }
        // openid template has enabled=false
        assertFalse(emailClaim.enabled)
    }

    @Test
    fun `provideClaims - Error when referencing default template explicitly`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string", template = "default")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
    }

    @Test
    fun `provideClaims - Error when referencing nonexistent template`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string", template = "nonexistent")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(DisabledClaimsConfig::class.java, result)
    }

    // endregion

    // region Claim field resolution

    @Test
    fun `provideClaims - Claim fields override template`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = "openid", enabled = "true")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val emailClaim = (result as EnabledClaimsConfig).claims.first { it.id == "email" }
        // Claim overrides template's enabled=false with true
        assertTrue(emailClaim.enabled)
    }

    @Test
    fun `provideClaims - type is required`() {
        val properties = listOf(
            claimProperties(id = "department") // no type
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
            claimProperties(id = "email", type = "email", template = "openid", verifiedId = "email_verified")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val emailClaim = (result as EnabledClaimsConfig).claims.first { it.id == "email" }
        assertEquals("email_verified", emailClaim.verifiedId)
    }

    // endregion

    // region Generated claims

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
            assertFalse(claim.userInputted, "Generated claim ${generated.id} should not be user-inputted")
        }
    }

    @Test
    fun `provideClaims - Generated claims are not duplicated when also in properties`() {
        val properties = listOf(
            claimProperties(id = "sub", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claims = (result as EnabledClaimsConfig).claims
        val subClaims = claims.filter { it.id == "sub" }
        assertEquals(1, subClaims.size)
    }

    // endregion

    // region userInputted

    @Test
    fun `provideClaims - userInputted is true when ACL allows user write`() {
        val writableAcl = defaultAcl.copy(
            consent = defaultAcl.consent.copy(writableByUser = true)
        )
        every {
            claimAclFactory.buildAcl(any(), any(), any(), any(), any())
        } returns writableAcl

        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertTrue(claim.userInputted)
    }

    @Test
    fun `provideClaims - userInputted is false when ACL disallows user write`() {
        val properties = listOf(
            claimProperties(id = "department", type = "string")
        )

        val result = factory.provideClaims(properties)

        assertInstanceOf(EnabledClaimsConfig::class.java, result)
        val claim = (result as EnabledClaimsConfig).claims.first { it.id == "department" }
        assertFalse(claim.userInputted)
    }

    // endregion

    // region Origin

    @Test
    fun `provideClaims - OpenID claim has OPENID_CONNECT origin`() {
        val properties = listOf(
            claimProperties(id = "email", type = "email", template = "openid", enabled = "true")
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

    // endregion
}
