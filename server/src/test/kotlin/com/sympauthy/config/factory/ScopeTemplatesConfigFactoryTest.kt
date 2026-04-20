package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.DisabledScopeTemplatesConfig
import com.sympauthy.config.model.EnabledScopeTemplatesConfig
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeTemplatesConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    @InjectMockKs
    lateinit var factory: ScopeTemplatesConfigFactory

    private fun scopeTemplateProperties(
        id: String,
        type: String? = null,
        enabled: String? = null
    ): ScopeTemplateConfigurationProperties {
        return ScopeTemplateConfigurationProperties(id).apply {
            this.type = type
        }.also {
            // enabled is val, so we set it via reflection
            if (enabled != null) {
                val field = ScopeTemplateConfigurationProperties::class.java.getDeclaredField("enabled")
                field.isAccessible = true
                field.set(it, enabled)
            }
        }
    }

    @Test
    fun `provideScopeTemplates - Returns enabled config with valid templates`() {
        val templates = listOf(
            scopeTemplateProperties(id = "default_openid", enabled = "false"),
            scopeTemplateProperties(id = "default_custom", type = "consentable")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        assertEquals(2, config.templates.size)

        val openidTemplate = config.templates["default_openid"]!!
        assertEquals(false, openidTemplate.enabled)
        assertNull(openidTemplate.type)

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
    fun `provideScopeTemplates - All fields are nullable`() {
        val templates = listOf(
            scopeTemplateProperties(id = "minimal")
        )

        val result = factory.provideScopeTemplates(templates)

        assertInstanceOf(EnabledScopeTemplatesConfig::class.java, result)
        val config = result as EnabledScopeTemplatesConfig
        val template = config.templates["minimal"]!!
        assertNull(template.enabled)
        assertNull(template.type)
    }
}
