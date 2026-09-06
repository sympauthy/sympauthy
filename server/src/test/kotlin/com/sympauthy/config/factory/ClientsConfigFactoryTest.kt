package com.sympauthy.config.factory

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ClientConfigFieldParser
import com.sympauthy.config.parsing.ClientsConfigParser
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.validation.ClientConfigFieldValidator
import com.sympauthy.config.validation.ClientsConfigValidator
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class ClientsConfigFactoryTest {

    @MockK(relaxed = true)
    lateinit var fieldParser: ClientConfigFieldParser

    @MockK(relaxed = true)
    lateinit var fieldValidator: ClientConfigFieldValidator

    private val parser = ConfigParser()

    private fun setUpFieldValidator() {
        // By default, validateWebhook passes through null.
        coEvery { fieldValidator.validateWebhook(any()) } returns null
    }

    /**
     * Makes the validator pass the parsed grant types through.
     *
     * Only a client that gets as far as being validated reaches it: one whose template could not be
     * resolved is refused before that.
     */
    private fun passGrantTypesThrough() {
        coEvery { fieldValidator.validateGrantTypes(any(), any(), any()) } answers { thirdArg() }
    }

    private fun clientProperties(
        id: String,
        template: String? = null,
        public: Boolean? = null,
        secret: String? = null,
        allowedGrantTypes: List<String>? = null,
        allowedRedirectUris: List<String>? = null,
        allowedScopes: List<String>? = null
    ): ClientConfigurationProperties {
        return ClientConfigurationProperties(id).apply {
            this.template = template
            this.public = public
            this.secret = secret
            this.allowedGrantTypes = allowedGrantTypes
            this.allowedRedirectUris = allowedRedirectUris
            this.allowedScopes = allowedScopes
        }
    }

    private val testAudience = Audience(id = "test-audience", tokenAudience = "test-audience")

    private fun clientTemplate(
        id: String,
        audience: String? = "test-audience",
        public: Boolean? = null,
        allowedGrantTypes: Set<GrantType>? = null,
        authorizationFlow: AuthorizationFlow? = null,
        allowedRedirectUris: List<String>? = null
    ): ClientTemplate {
        return ClientTemplate(
            id = id,
            audienceId = audience,
            public = public,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlow = authorizationFlow,
            allowedRedirectUris = allowedRedirectUris,
            allowedScopes = null,
            defaultScopes = null,
            authorizationWebhook = null,
            accessReviewWebhook = null
        )
    }

    private fun factory(vararg templates: ClientTemplate): ClientsConfigFactory {
        setUpFieldValidator()
        val templatesConfig = EnabledClientTemplatesConfig(templates.associateBy { it.id })
        val templatesFlow = flowOf<ClientTemplatesConfig>(templatesConfig)
        val audiencesConfig = EnabledAudiencesConfig(listOf(testAudience))
        return ClientsConfigFactory(
            ClientsConfigParser(parser, fieldParser),
            ClientsConfigValidator(fieldValidator),
            templatesFlow,
            audiencesConfig,
            EnabledScopesConfig(emptyList()),
            EnabledAuthorizationFlowsConfig(mockk<InteractiveFlow>(relaxed = true), emptyList()),
            EnabledUrlsConfig(root = URI.create("https://auth.example.com"))
        )
    }

    /**
     * A factory resolving a client's scopes against [scopes] for real, which the other tests double
     * out. It is what decides whether a client may name a scope the deployment turned off.
     */
    private fun factoryServing(scopes: List<Scope>, vararg templates: ClientTemplate): ClientsConfigFactory {
        val templatesConfig = EnabledClientTemplatesConfig(templates.associateBy { it.id })
        return ClientsConfigFactory(
            ClientsConfigParser(parser, fieldParser),
            ClientsConfigValidator(ClientConfigFieldValidator()),
            flowOf<ClientTemplatesConfig>(templatesConfig),
            EnabledAudiencesConfig(listOf(testAudience)),
            EnabledScopesConfig(scopes),
            EnabledAuthorizationFlowsConfig(mockk<InteractiveFlow>(relaxed = true), emptyList()),
            EnabledUrlsConfig(root = URI.create("https://auth.example.com"))
        )
    }

    @Test
    fun `provideClients - Client may name a scope this deployment serves`() = runTest {
        val factory = factoryServing(
            listOf(ConsentableUserScope("my-scope")),
            clientTemplate(
                id = "default",
                allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE),
                allowedRedirectUris = listOf("https://example.com/callback")
            )
        )
        val clients = listOf(clientProperties(id = "my-app", secret = "secret", allowedScopes = listOf("my-scope")))

        val result = factory.provideClients(clients).first()

        assertInstanceOf(EnabledClientsConfig::class.java, result)
        assertEquals(
            listOf("my-scope"),
            (result as EnabledClientsConfig).clients.first().allowedScopes?.map { it.scope }
        )
    }

    @Test
    fun `provideClients - Client may not name a scope this deployment turned off`() = runTest {
        val factory = factoryServing(
            listOf(DisabledScope("my-scope", ScopeType.CONSENTABLE)),
            clientTemplate(
                id = "default",
                allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE),
                allowedRedirectUris = listOf("https://example.com/callback")
            )
        )
        val clients = listOf(clientProperties(id = "my-app", secret = "secret", allowedScopes = listOf("my-scope")))

        val result = factory.provideClients(clients).first()

        val error = assertInstanceOf(DisabledClientsConfig::class.java, result).configurationErrors!!.first()
        assertTrue(error.message!!.contains("config.client.scope.invalid"))
    }

    @Test
    fun `provideClients - Client inherits grant types from default template`() = runTest {
        val grantTypes = setOf(GrantType.AUTHORIZATION_CODE)
        val redirectUris = listOf("https://example.com/callback")

        passGrantTypesThrough()

        val factory = factory(
            clientTemplate(
                id = "default",
                allowedGrantTypes = grantTypes,
                allowedRedirectUris = redirectUris
            )
        )
        val clients = listOf(
            clientProperties(id = "my-app", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(EnabledClientsConfig::class.java, result)
        val config = result as EnabledClientsConfig
        val client = config.clients.first()
        assertEquals(grantTypes, client.allowedGrantTypes)
    }

    @Test
    fun `provideClients - Client property overrides default template`() = runTest {
        val templateGrantTypes = setOf(GrantType.AUTHORIZATION_CODE)
        val clientGrantTypes = setOf(GrantType.CLIENT_CREDENTIALS)
        val redirectUris = listOf("https://example.com/callback")

        coEvery { fieldParser.parseGrantTypes(any(), any(), any()) } returns clientGrantTypes
        passGrantTypesThrough()

        val factory = factory(
            clientTemplate(
                id = "default",
                allowedGrantTypes = templateGrantTypes,
                allowedRedirectUris = redirectUris
            )
        )
        val clients = listOf(
            clientProperties(
                id = "my-app",
                secret = "secret",
                allowedGrantTypes = listOf("client_credentials")
            )
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(EnabledClientsConfig::class.java, result)
        val config = result as EnabledClientsConfig
        val client = config.clients.first()
        assertEquals(clientGrantTypes, client.allowedGrantTypes)
    }

    @Test
    fun `provideClients - Client with explicit template uses that template instead of default`() = runTest {
        val defaultFlow = mockk<AuthorizationFlow>()
        val customFlow = mockk<AuthorizationFlow>()
        val grantTypes = setOf(GrantType.CLIENT_CREDENTIALS)

        passGrantTypesThrough()

        val factory = factory(
            clientTemplate(id = "default", authorizationFlow = defaultFlow, allowedGrantTypes = grantTypes),
            clientTemplate(id = "custom", authorizationFlow = customFlow, allowedGrantTypes = grantTypes)
        )
        val clients = listOf(
            clientProperties(id = "my-app", template = "custom", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(EnabledClientsConfig::class.java, result)
        val config = result as EnabledClientsConfig
        val client = config.clients.first()
        assertSame(customFlow, client.authorizationFlow)
    }

    @Test
    fun `provideClients - Referencing default template by name produces error`() = runTest {
        val factory = factory(
            clientTemplate(id = "default", allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE))
        )
        val clients = listOf(
            clientProperties(id = "my-app", template = "default", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(DisabledClientsConfig::class.java, result)
        val config = result as DisabledClientsConfig
        val error = config.configurationErrors!!.filterIsInstance<ConfigurationException>().first()
        assertEquals("config.client.template.cannot_reference_default", error.messageId)
    }

    @Test
    fun `provideClients - Referencing nonexistent template produces error`() = runTest {
        val factory = factory()
        val clients = listOf(
            clientProperties(id = "my-app", template = "nonexistent", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(DisabledClientsConfig::class.java, result)
        val config = result as DisabledClientsConfig
        val error = config.configurationErrors!!.filterIsInstance<ConfigurationException>().first()
        assertEquals("config.client.template.not_found", error.messageId)
    }

    @Test
    fun `provideClients - Client without template and no default requires all fields`() = runTest {
        val factory = factory()
        val clients = listOf(
            clientProperties(id = "my-app", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(DisabledClientsConfig::class.java, result)
    }

    @Test
    fun `provideClients - Public client inherits public from default template`() = runTest {
        val grantTypes = setOf(GrantType.CLIENT_CREDENTIALS)

        passGrantTypesThrough()

        val factory = factory(
            clientTemplate(id = "default", public = true, allowedGrantTypes = grantTypes)
        )
        val clients = listOf(
            clientProperties(id = "my-app")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(EnabledClientsConfig::class.java, result)
        val config = result as EnabledClientsConfig
        val client = config.clients.first()
        assertTrue(client.public)
        assertNull(client.secret)
    }
}
