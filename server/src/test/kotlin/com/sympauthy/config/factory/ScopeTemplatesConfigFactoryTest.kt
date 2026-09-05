package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledScopeTemplatesConfig
import com.sympauthy.config.model.EnabledScopeTemplatesConfig
import com.sympauthy.config.model.ScopeTemplatesConfig
import com.sympauthy.config.parsing.ScopeTemplatesConfigParser
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties
import com.sympauthy.config.validation.ScopeTemplatesConfigValidator
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeTemplatesConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    lateinit var factory: ScopeTemplatesConfigFactory

    @BeforeEach
    fun setUp() {
        factory = ScopeTemplatesConfigFactory(
            ScopeTemplatesConfigParser(parser),
            ScopeTemplatesConfigValidator()
        )
    }

    private fun scopeTemplateProperties(
        id: String,
        type: String? = null,
        enabled: String? = null,
        discoverable: String? = null,
        audience: String? = null
    ): ScopeTemplateConfigurationProperties {
        return ScopeTemplateConfigurationProperties(id).apply {
            this.type = type
            this.enabled = enabled
            this.discoverable = discoverable
            this.audience = audience
        }
    }

    /** Every configuration error, as the key it was reported at mapped to the code reported there. */
    private fun ScopeTemplatesConfig.errorsByKey(): Map<String, String> {
        assertInstanceOf(DisabledScopeTemplatesConfig::class.java, this)
        return (this as DisabledScopeTemplatesConfig).configurationErrors!!
            .associate { (it as ConfigurationException).key to it.messageId }
    }

    @Test
    fun `provideScopeTemplates - Returns enabled config with valid templates`() {
        val templates = listOf(
            scopeTemplateProperties(id = "my_template", enabled = "false"),
            scopeTemplateProperties(id = "default_custom", type = "consentable")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertEquals(2, config.templates.size)

        val namedTemplate = config.templates["my_template"]!!
        assertEquals(false, namedTemplate.enabled)
        assertNull(namedTemplate.type)

        val customTemplate = config.templates["default_custom"]!!
        assertNull(customTemplate.enabled)
        assertEquals("consentable", customTemplate.type)
    }

    @Test
    fun `provideScopeTemplates - Returns enabled config when no templates defined`() {
        val result = factory.provideScopeTemplates(emptyList())

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertTrue(config.templates.isEmpty())
    }

    @Test
    fun `provideScopeTemplates - Returns disabled config when template has invalid type`() {
        val templates = listOf(
            scopeTemplateProperties(id = "bad_template", type = "invalid")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(DisabledScopeTemplatesConfig::class.java, result)
    }

    @Test
    fun `provideScopeTemplates - Allows client type in scope template`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_client", type = "client")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertEquals("client", config.templates["default_client"]!!.type)
    }

    @Test
    fun `provideScopeTemplates - Carry the audience a template defines for the custom scopes using it`() {
        val templates = listOf(
            scopeTemplateProperties(id = "partner_scopes", audience = "partners")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertEquals("partners", config.templates["partner_scopes"]!!.audienceId)
    }

    @Test
    fun `provideScopeTemplates - Carry the discoverability a template defines for the scopes using it`() {
        val templates = listOf(
            scopeTemplateProperties(id = "internal_scopes", discoverable = "false")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertEquals(false, config.templates["internal_scopes"]!!.discoverable)
    }

    @Test
    fun `provideScopeTemplates - Report a discoverability that is not a boolean`() {
        val templates = listOf(
            scopeTemplateProperties(id = "internal_scopes", discoverable = "maybe")
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf("templates.scopes.internal_scopes.discoverable" to "config.invalid_boolean"),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - Refuse an audience on the template applied to the OpenID Connect scopes`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_openid", audience = "partners")
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf(
                "templates.scopes.default_openid.audience"
                        to "config.scope.template.audience_not_allowed_on_default_openid"
            ),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - Refuse a type on the template applied to the OpenID Connect scopes`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_openid", type = "grantable")
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf(
                "templates.scopes.default_openid.type"
                        to "config.scope.template.type_not_allowed_on_default_openid"
            ),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - Refuse turning the OpenID Connect scopes off as a set`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_openid", enabled = "false")
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf(
                "templates.scopes.default_openid.enabled"
                        to "config.scope.template.enabled_not_allowed_on_default_openid"
            ),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - Refuse hiding the OpenID Connect scopes from discovery as a set`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_openid", discoverable = "false")
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf(
                "templates.scopes.default_openid.discoverable"
                        to "config.scope.template.discoverable_not_allowed_on_default_openid"
            ),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - Report every setting the OpenID Connect template may not carry`() {
        val templates = listOf(
            scopeTemplateProperties(
                id = "default_openid", enabled = "false", discoverable = "false", type = "grantable",
                audience = "partners"
            )
        )

        val result = factory.provideScopeTemplates(templates)

        assertEquals(
            mapOf(
                "templates.scopes.default_openid.enabled"
                        to "config.scope.template.enabled_not_allowed_on_default_openid",
                "templates.scopes.default_openid.discoverable"
                        to "config.scope.template.discoverable_not_allowed_on_default_openid",
                "templates.scopes.default_openid.type"
                        to "config.scope.template.type_not_allowed_on_default_openid",
                "templates.scopes.default_openid.audience"
                        to "config.scope.template.audience_not_allowed_on_default_openid"
            ),
            result.errorsByKey()
        )
    }

    @Test
    fun `provideScopeTemplates - All fields are nullable`() {
        val templates = listOf(
            scopeTemplateProperties(id = "minimal")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        val template = config.templates["minimal"]!!
        assertNull(template.enabled)
        assertNull(template.discoverable)
        assertNull(template.type)
    }
}
