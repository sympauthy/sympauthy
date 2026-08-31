package com.sympauthy.config.factory

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.oauth2.BuiltInGrantableScope
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
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
        audiences: List<String> = emptyList(),
        adminConfig: AdminConfig = DisabledAdminConfig(emptyList())
    ): ScopeConfigFactory {
        return ScopeConfigFactory(
            ScopeConfigParser(parser),
            ScopeConfigValidator(),
            EnabledScopeTemplatesConfig(templates.associateBy { it.id }),
            EnabledAudiencesConfig(audiences.map { Audience(id = it, tokenAudience = it) }),
            adminConfig
        )
    }

    private fun scopeProperties(
        id: String,
        type: String? = null,
        template: String? = null,
        enabled: String? = null,
        audience: String? = null
    ): ScopeConfigurationProperties {
        return ScopeConfigurationProperties(id).apply {
            this.type = type
            this.template = template
            this.enabled = enabled
            this.audience = audience
        }
    }

    private fun ScopesConfig.scopeNamed(id: String): EnabledScope? {
        return (this as EnabledScopesConfig).scopes.firstOrNull { it.scope == id }
    }

    private fun ScopesConfig.onlyError(): ConfigurationException {
        val errors = errors()
        assertEquals(1, errors.size)
        return errors.first()
    }

    /** Every configuration error, as the key it was reported at mapped to the code reported there. */
    private fun ScopesConfig.errorsByKey(): Map<String, String> {
        return errors().associate { it.key to it.messageId }
    }

    private fun ScopesConfig.errors(): List<ConfigurationException> {
        assertInstanceOf(DisabledScopesConfig::class.java, this)
        return (this as DisabledScopesConfig).configurationErrors!!.map { it as ConfigurationException }
    }

    // --- OpenID Connect scope templates ---

    @Test
    fun `provideScopes - Take an OpenID Connect scope off from the template it names`() {
        val factory = factoryWith(
            templates = listOf(ScopeTemplate(id = "my-template", enabled = false, type = null, audienceId = null))
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "profile", template = "my-template")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertNull(result.scopeNamed("profile"))
    }

    @Test
    fun `provideScopes - Let an OpenID Connect scope property override its template`() {
        val factory = factoryWith(
            templates = listOf(ScopeTemplate(id = "my-template", enabled = false, type = null, audienceId = null))
        )

        val result = factory.provideScopes(
            listOf(scopeProperties(id = "email", template = "my-template", enabled = "true"))
        )

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

    // --- Audience ---

    @Test
    fun `provideScopes - Bind a custom scope to the audience it names`() {
        val factory = factoryWith(audiences = listOf("partners"))

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope", audience = "partners")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertEquals("partners", result.scopeNamed("my-scope")!!.audienceId)
    }

    @Test
    fun `provideScopes - Bind a custom scope to the audience its template names`() {
        val factory = factoryWith(
            audiences = listOf("partners"),
            templates = listOf(
                ScopeTemplate(id = "default_custom", enabled = null, type = null, audienceId = "partners")
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope")))

        assertInstanceOf(EnabledScopesConfig::class.java, result)
        assertEquals("partners", result.scopeNamed("my-scope")!!.audienceId)
    }

    @Test
    fun `provideScopes - Report an audience a custom scope inherits from a template at that template`() {
        val factory = factoryWith(
            templates = listOf(
                ScopeTemplate(id = "default_custom", enabled = null, type = null, audienceId = "nonexistent")
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "my-scope")))

        val error = result.onlyError()
        assertEquals("templates.scopes.default_custom.audience", error.key)
        assertEquals("config.scope.audience.not_found", error.messageId)
    }

    @Test
    fun `provideScopes - Refuse an audience named on an OpenID Connect scope`() {
        val factory = factoryWith(audiences = listOf("partners"))

        val result = factory.provideScopes(listOf(scopeProperties(id = "email", audience = "partners")))

        val error = result.onlyError()
        assertEquals("scopes.email.audience", error.key)
        assertEquals("config.scope.audience.not_allowed_for_openid", error.messageId)
    }

    @Test
    fun `provideScopes - Refuse an audience an OpenID Connect scope inherits from the template it names`() {
        val factory = factoryWith(
            audiences = listOf("partners"),
            templates = listOf(
                ScopeTemplate(id = "my-template", enabled = null, type = null, audienceId = "partners")
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "email", template = "my-template")))

        val error = result.onlyError()
        assertEquals("scopes.email.template", error.key)
        assertEquals("config.scope.template.audience_not_allowed_for_openid", error.messageId)
    }

    @Test
    fun `provideScopes - Refuse a type named on an OpenID Connect scope`() {
        val result = factory.provideScopes(listOf(scopeProperties(id = "email", type = "grantable")))

        val error = result.onlyError()
        assertEquals("scopes.email.type", error.key)
        assertEquals("config.scope.type.not_allowed_for_openid", error.messageId)
    }

    @Test
    fun `provideScopes - Refuse a type an OpenID Connect scope inherits from the template it names`() {
        val factory = factoryWith(
            templates = listOf(
                ScopeTemplate(id = "my-template", enabled = null, type = "grantable", audienceId = null)
            )
        )

        val result = factory.provideScopes(listOf(scopeProperties(id = "email", template = "my-template")))

        val error = result.onlyError()
        assertEquals("scopes.email.template", error.key)
        assertEquals("config.scope.template.type_not_allowed_for_openid", error.messageId)
    }

    @Test
    fun `provideScopes - Report every setting an OpenID Connect scope may not carry`() {
        val factory = factoryWith(audiences = listOf("partners"))

        val result = factory.provideScopes(
            listOf(scopeProperties(id = "email", type = "grantable", audience = "partners"))
        )

        assertEquals(
            mapOf(
                "scopes.email.audience" to "config.scope.audience.not_allowed_for_openid",
                "scopes.email.type" to "config.scope.type.not_allowed_for_openid"
            ),
            result.errorsByKey()
        )
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
