package com.sympauthy.config.factory

import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.DisabledClientsConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledClientsConfig
import com.sympauthy.config.properties.ClientConfigurationProperties
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClientsConfigFactoryTest {

    @MockK(relaxed = true)
    lateinit var fieldParser: ClientConfigFieldParser

    private val parser = ConfigParser()

    private fun clientProperties(
        id: String,
        template: String? = null,
        public: Boolean? = null,
        secret: String? = null,
        allowedGrantTypes: List<String>? = null,
        allowedRedirectUris: List<String>? = null
    ): ClientConfigurationProperties {
        return ClientConfigurationProperties(id).apply {
            this.template = template
            this.`public` = public
            this.secret = secret
            this.allowedGrantTypes = allowedGrantTypes
            this.allowedRedirectUris = allowedRedirectUris
        }
    }

    private fun clientTemplate(
        id: String,
        public: Boolean? = null,
        allowedGrantTypes: Set<GrantType>? = null,
        authorizationFlow: AuthorizationFlow? = null,
        allowedRedirectUris: List<String>? = null
    ): ClientTemplate {
        return ClientTemplate(
            id = id,
            public = public,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlow = authorizationFlow,
            allowedRedirectUris = allowedRedirectUris,
            allowedScopes = null,
            defaultScopes = null,
            authorizationWebhook = null
        )
    }

    private fun factory(vararg templates: ClientTemplate): ClientsConfigFactory {
        val templatesConfig = EnabledClientTemplatesConfig(templates.associateBy { it.id })
        val templatesFlow = flowOf<ClientTemplatesConfig>(templatesConfig)
        return ClientsConfigFactory(parser, fieldParser, templatesFlow)
    }

    // --- Default template resolution ---

    @Test
    fun `Client inherits grant types from default template`() = runTest {
        val grantTypes = setOf(GrantType.AUTHORIZATION_CODE)
        val redirectUris = listOf("https://example.com/callback")

        coEvery { fieldParser.getAllowedRedirectUris(any(), any(), any(), any()) } returns redirectUris

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
    fun `Client property overrides default template`() = runTest {
        val templateGrantTypes = setOf(GrantType.AUTHORIZATION_CODE)
        val clientGrantTypes = setOf(GrantType.CLIENT_CREDENTIALS)
        val redirectUris = listOf("https://example.com/callback")

        coEvery { fieldParser.getAllowedGrantTypes(any(), any(), any()) } returns clientGrantTypes

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

    // --- Explicit custom template ---

    @Test
    fun `Client with explicit template uses that template instead of default`() = runTest {
        val defaultFlow = mockk<AuthorizationFlow>()
        val customFlow = mockk<AuthorizationFlow>()
        val grantTypes = setOf(GrantType.CLIENT_CREDENTIALS)

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

    // --- Template validation errors ---

    @Test
    fun `Referencing default template by name produces error`() = runTest {
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
    fun `Referencing nonexistent template produces error`() = runTest {
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

    // --- No template ---

    @Test
    fun `Client without template and no default requires all fields`() = runTest {
        coEvery { fieldParser.getAllowedGrantTypes(any(), isNull(), any()) } answers {
            val errors = thirdArg<MutableList<ConfigurationException>>()
            errors.add(ConfigurationException("key", "config.client.allowed_grant_types.missing"))
            null
        }
        coEvery { fieldParser.getAllowedRedirectUris(any(), any(), isNull(), any()) } answers {
            val errors = arg<MutableList<ConfigurationException>>(3)
            errors.add(ConfigurationException("key", "config.client.allowed_redirect_uris.missing"))
            null
        }

        val factory = factory()
        val clients = listOf(
            clientProperties(id = "my-app", secret = "secret")
        )

        val result = factory.provideClients(clients).first()

        assertInstanceOf(DisabledClientsConfig::class.java, result)
    }

    @Test
    fun `Public client inherits public from default template`() = runTest {
        val grantTypes = setOf(GrantType.CLIENT_CREDENTIALS)

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
