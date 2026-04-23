package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ScopeConfigurationProperties
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    @SpyK
    var scopeTemplatesConfig: ScopeTemplatesConfig = EnabledScopeTemplatesConfig(emptyMap())

    @SpyK
    var uncheckedAudiencesConfig: AudiencesConfig = EnabledAudiencesConfig(emptyList())

    @InjectMockKs
    lateinit var factory: ScopeConfigFactory

    private fun scopeProperties(
        id: String,
        type: String? = null,
        template: String? = null,
        enabled: String? = null
    ): ScopeConfigurationProperties {
        return ScopeConfigurationProperties(id).apply {
            this.type = type
            this.template = template
        }.also {
            if (enabled != null) {
                val field = ScopeConfigurationProperties::class.java.getDeclaredField("enabled")
                field.isAccessible = true
                field.set(it, enabled)
            }
        }
    }

    private fun withTemplates(vararg templates: ScopeTemplate): ScopeConfigFactory {
        val config = EnabledScopeTemplatesConfig(templates.associateBy { it.id })
        return ScopeConfigFactory(parser, config, EnabledAudiencesConfig(emptyList()))
    }

    // --- OpenID Connect scope with default_openid template ---

    @Test
    fun `OpenID scope gets default_openid template applied`() {
        val factory = withTemplates(ScopeTemplate(id = "default_openid", enabled = false, type = null, audience = null))
        val scopes = listOf(scopeProperties(id = "profile"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as OpenIdConnectScopeConfig
        assertEquals("profile", scope.scope)
        assertEquals(false, scope.enabled)
    }

    @Test
    fun `OpenID scope property overrides template`() {
        val factory = withTemplates(ScopeTemplate(id = "default_openid", enabled = false, type = null, audience = null))
        val scopes = listOf(scopeProperties(id = "email", enabled = "true"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as OpenIdConnectScopeConfig
        assertEquals(true, scope.enabled)
    }

    @Test
    fun `OpenID scope defaults to enabled when no template`() {
        val scopes = listOf(scopeProperties(id = "profile"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as OpenIdConnectScopeConfig
        assertEquals(true, scope.enabled)
    }

    // --- Custom scope with default_custom template ---

    @Test
    fun `Custom scope gets default_custom template type applied`() {
        val factory = withTemplates(ScopeTemplate(id = "default_custom", enabled = null, type = "consentable", audience = null))
        val scopes = listOf(scopeProperties(id = "my-scope"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as CustomScopeConfig
        assertEquals("my-scope", scope.scope)
        assertEquals(true, scope.consentable)
    }

    @Test
    fun `Custom scope property overrides template type`() {
        val factory = withTemplates(ScopeTemplate(id = "default_custom", enabled = null, type = "consentable", audience = null))
        val scopes = listOf(scopeProperties(id = "my-scope", type = "grantable"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as CustomScopeConfig
        assertEquals(false, scope.consentable)
    }

    @Test
    fun `Custom scope defaults to grantable when no template`() {
        val scopes = listOf(scopeProperties(id = "my-scope"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as CustomScopeConfig
        assertEquals(false, scope.consentable)
    }

    // --- Explicit custom template ---

    @Test
    fun `Custom scope with explicit template uses that template`() {
        val factory = withTemplates(
            ScopeTemplate(id = "default_custom", enabled = null, type = "grantable", audience = null),
            ScopeTemplate(id = "my-template", enabled = null, type = "consentable", audience = null)
        )
        val scopes = listOf(scopeProperties(id = "my-scope", template = "my-template"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        val config = result as EnabledScopesConfig
        val scope = config.scopes.first() as CustomScopeConfig
        assertEquals(true, scope.consentable)
    }

    // --- Template validation errors ---

    @Test
    fun `Referencing default template by name produces error`() {
        val factory = withTemplates(ScopeTemplate(id = "default_openid", enabled = null, type = null, audience = null))
        val scopes = listOf(scopeProperties(id = "my-scope", template = "default_openid"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(DisabledScopesConfig::class.java, result)
        val config = result as DisabledScopesConfig
        val error = config.configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.scope.template.cannot_reference_default"))
    }

    @Test
    fun `Referencing nonexistent template produces error`() {
        val scopes = listOf(scopeProperties(id = "my-scope", template = "nonexistent"))

        val result = factory.provideScopes(scopes)

        assertInstanceOf(DisabledScopesConfig::class.java, result)
        val config = result as DisabledScopesConfig
        val error = config.configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.scope.template.not_found"))
    }
}
