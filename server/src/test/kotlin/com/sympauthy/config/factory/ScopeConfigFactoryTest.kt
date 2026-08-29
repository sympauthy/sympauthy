package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.oauth2.BuiltInGrantableScope
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ScopeConfigParser
import com.sympauthy.config.properties.ScopeConfigurationProperties
import com.sympauthy.config.validation.ScopeConfigValidator
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    lateinit var factory: ScopeConfigFactory

    @BeforeEach
    fun setUp() {
        factory = factoryWith()
    }

    private fun factoryWith(
        templates: List<ScopeTemplate> = emptyList(),
        adminConfig: AdminConfig = DisabledAdminConfig(emptyList())
    ): ScopeConfigFactory {
        return ScopeConfigFactory(
            ScopeConfigParser(parser),
            ScopeConfigValidator(),
            EnabledScopeTemplatesConfig(templates.associateBy { it.id }),
            EnabledAudiencesConfig(emptyList()),
            adminConfig
        )
    }

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

    private fun ScopesConfig.scopeNamed(id: String): Scope? {
        return (this as EnabledScopesConfig).scopes.firstOrNull { it.scope == id }
    }

    // --- OpenID Connect scope with default_openid template ---

    @Test
    fun `provideScopes - Apply default_openid template to an OpenID Connect scope`() {
        val factory = factoryWith(
            templates = listOf(ScopeTemplate(id = "default_openid", enabled = false, type = null, audienceId = null))
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "profile")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertNull(result.scopeNamed("profile"))
    }

    @Test
    fun `provideScopes - Let an OpenID Connect scope property override its template`() {
        val factory = factoryWith(
            templates = listOf(ScopeTemplate(id = "default_openid", enabled = false, type = null, audienceId = null))
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "email", enabled = "true")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(ConsentableUserScope::class.java, result.scopeNamed("email"))
    }

    @Test
    fun `provideScopes - Enable an OpenID Connect scope when no template applies`() {
        val result = factory.provideScopes(listOf(scopeProperties(id = "profile")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(ConsentableUserScope::class.java, result.scopeNamed("profile"))
    }

    @Test
    fun `provideScopes - Serve every OpenID Connect scope the deployment did not disable`() {
        val result = factory.provideScopes(emptyList())

        OpenIdConnectScope.entries.forEach {
            assertInstanceOf(ConsentableUserScope::class.java, result.scopeNamed(it.scope))
        }
    }

    // --- Custom scope with default_custom template ---

    @Test
    fun `provideScopes - Apply default_custom template type to a custom scope`() {
        val factory = factoryWith(
            templates = listOf(
                ScopeTemplate(id = "default_custom", enabled = null, type = "consentable", audienceId = null)
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(ConsentableUserScope::class.java, result.scopeNamed("my-scope"))
    }

    @Test
    fun `provideScopes - Let a custom scope property override its template type`() {
        val factory = factoryWith(
            templates = listOf(
                ScopeTemplate(id = "default_custom", enabled = null, type = "consentable", audienceId = null)
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope", type = "grantable")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(GrantableUserScope::class.java, result.scopeNamed("my-scope"))
    }

    @Test
    fun `provideScopes - Default a custom scope to grantable when no template applies`() {
        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(GrantableUserScope::class.java, result.scopeNamed("my-scope"))
    }

    // --- Explicit custom template ---

    @Test
    fun `provideScopes - Use the template a custom scope names`() {
        val factory = factoryWith(
            templates = listOf(
                ScopeTemplate(id = "default_custom", enabled = null, type = "grantable", audienceId = null),
                ScopeTemplate(id = "my-template", enabled = null, type = "consentable", audienceId = null)
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope", template = "my-template")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertInstanceOf(ConsentableUserScope::class.java, result.scopeNamed("my-scope"))
    }

    // --- Scopes the server defines itself ---

    @Test
    fun `provideScopes - Serve the built-in grantable and client scopes`() {
        val result = factory.provideScopes(emptyList())

        BuiltInGrantableScope.entries.forEach {
            val scope = result.scopeNamed(it.scope)
            assertInstanceOf(GrantableUserScope::class.java, scope)
            assertEquals(it.discoverable, scope!!.discoverable)
        }
        BuiltInClientScope.entries.forEach {
            assertInstanceOf(ClientScope::class.java, result.scopeNamed(it.scope))
        }
    }

    @Test
    fun `provideScopes - Bind the admin scopes to the configured admin audience`() {
        val factory = factoryWith(
            adminConfig = EnabledAdminConfig(enabled = true, integratedUi = true, audienceId = "admin")
        )

        val result = factory.provideScopes(emptyList())

        AdminScope.entries.forEach { adminScope ->
            val scope = result.scopeNamed(adminScope.scope)
            assertInstanceOf(GrantableUserScope::class.java, scope)
            assertFalse(scope!!.discoverable)
            assertEquals("admin", scope.audienceId)
        }
    }

    @Test
    fun `provideScopes - Serve no admin scope when the administration API is not configured`() {
        val result = factory.provideScopes(emptyList())

        AdminScope.entries.forEach {
            assertNull(result.scopeNamed(it.scope))
        }
    }

    @Test
    fun `provideScopes - Reject a scope the server defines itself`() {
        val result = factory.provideScopes(listOf(scopeProperties(id = BuiltInGrantableScope.OPENID.scope)))

        assertInstanceOf(DisabledScopesConfig::class.java, result)
        val error = (result as DisabledScopesConfig).configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.scope.builtin_not_configurable"))
    }

    // --- Template validation errors ---

    @Test
    fun `provideScopes - Reject a scope referencing a default template by name`() {
        val factory = factoryWith(
            templates = listOf(ScopeTemplate(id = "default_openid", enabled = null, type = null, audienceId = null))
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope", template = "default_openid")))

        assertInstanceOf(DisabledScopesConfig::class.java, result)
        val error = (result as DisabledScopesConfig).configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.scope.template.cannot_reference_default"))
    }

    @Test
    fun `provideScopes - Reject a scope referencing a template that does not exist`() {
        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope", template = "nonexistent")))

        assertInstanceOf(DisabledScopesConfig::class.java, result)
        val error = (result as DisabledScopesConfig).configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.scope.template.not_found"))
    }
}
