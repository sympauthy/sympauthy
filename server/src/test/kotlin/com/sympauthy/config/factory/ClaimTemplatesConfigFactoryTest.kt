package com.sympauthy.config.factory

import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.model.DisabledClaimTemplatesConfig
import com.sympauthy.config.model.EnabledClaimTemplatesConfig
import com.sympauthy.config.model.EnabledScopesConfig
import com.sympauthy.config.parsing.ClaimAclParser
import com.sympauthy.config.parsing.ClaimTemplatesConfigParser
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties
import com.sympauthy.config.validation.ClaimAclValidator
import com.sympauthy.config.validation.ClaimTemplatesConfigValidator
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimTemplatesConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    lateinit var factory: ClaimTemplatesConfigFactory

    private fun setUp() {
        val claimAclParser = ClaimAclParser(parser)
        val claimAclValidator = ClaimAclValidator(EnabledScopesConfig(emptyList()))
        factory = ClaimTemplatesConfigFactory(
            ClaimTemplatesConfigParser(parser, claimAclParser),
            ClaimTemplatesConfigValidator(claimAclValidator)
        )
    }

    private fun templateProperties(
        id: String,
        enabled: String? = null,
        required: String? = null,
        group: String? = null
    ): ClaimTemplateConfigurationProperties {
        return ClaimTemplateConfigurationProperties(id).apply {
            this.group = group
        }.also {
            if (enabled != null) {
                val field = ClaimTemplateConfigurationProperties::class.java.getDeclaredField("enabled")
                field.isAccessible = true
                field.set(it, enabled)
            }
            if (required != null) {
                val field = ClaimTemplateConfigurationProperties::class.java.getDeclaredField("required")
                field.isAccessible = true
                field.set(it, required)
            }
        }
    }

    @Test
    fun `provideClaimTemplates - Returns enabled config with valid templates`() {
        setUp()
        val templates = listOf(
            templateProperties(id = "openid", enabled = "false"),
            templateProperties(id = "default", required = "true")
        )

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(EnabledClaimTemplatesConfig::class.java, result)
        val config = result as EnabledClaimTemplatesConfig
        assertEquals(2, config.templates.size)

        val openidTemplate = config.templates["openid"]!!
        assertEquals(false, openidTemplate.enabled)
        assertNull(openidTemplate.required)

        val defaultTemplate = config.templates["default"]!!
        assertNull(defaultTemplate.enabled)
        assertEquals(true, defaultTemplate.required)
    }

    @Test
    fun `provideClaimTemplates - Returns enabled config when no templates defined`() {
        setUp()

        val result = factory.provideClaimTemplates(emptyList())

        assertInstanceOf(EnabledClaimTemplatesConfig::class.java, result)
        assertTrue((result as EnabledClaimTemplatesConfig).templates.isEmpty())
    }

    @Test
    fun `provideClaimTemplates - All fields are nullable`() {
        setUp()
        val templates = listOf(templateProperties(id = "minimal"))

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(EnabledClaimTemplatesConfig::class.java, result)
        val template = (result as EnabledClaimTemplatesConfig).templates["minimal"]!!
        assertNull(template.enabled)
        assertNull(template.required)
        assertNull(template.group)
        assertNull(template.allowedValues)
    }

    @Test
    fun `provideClaimTemplates - Parses group as enum`() {
        setUp()
        val templates = listOf(templateProperties(id = "with_group", group = "identity"))

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(EnabledClaimTemplatesConfig::class.java, result)
        val template = (result as EnabledClaimTemplatesConfig).templates["with_group"]!!
        assertEquals(ClaimGroup.IDENTITY, template.group)
    }

    @Test
    fun `provideClaimTemplates - Returns disabled config for invalid boolean`() {
        setUp()
        val templates = listOf(templateProperties(id = "bad", enabled = "not_a_boolean"))

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(DisabledClaimTemplatesConfig::class.java, result)
    }

    @Test
    fun `provideClaimTemplates - Returns disabled config for invalid group`() {
        setUp()
        val templates = listOf(templateProperties(id = "bad", group = "nonexistent"))

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(DisabledClaimTemplatesConfig::class.java, result)
    }

    @Test
    fun `provideClaimTemplates - Accumulates errors from multiple templates`() {
        setUp()
        val templates = listOf(
            templateProperties(id = "bad1", enabled = "invalid"),
            templateProperties(id = "bad2", required = "nope")
        )

        val result = factory.provideClaimTemplates(templates)

        assertInstanceOf(DisabledClaimTemplatesConfig::class.java, result)
        val errors = (result as DisabledClaimTemplatesConfig).configurationErrors!!
        assertEquals(2, errors.size)
    }
}
