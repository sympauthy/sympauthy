package com.sympauthy.config.factory

import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_WEB_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.DisabledAuthorizationFlowsConfig
import com.sympauthy.config.model.DisabledUrlsConfig
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.parsing.AuthorizationFlowsConfigParser
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties
import com.sympauthy.config.validation.AuthorizationFlowsConfigValidator
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class AuthorizationFlowsConfigFactoryTest {

    @MockK
    lateinit var mfaConfig: MfaConfig

    private val parser = ConfigParser()

    private fun factory(urlsConfig: UrlsConfig): AuthorizationFlowsConfigFactory {
        return AuthorizationFlowsConfigFactory(
            AuthorizationFlowsConfigParser(parser),
            AuthorizationFlowsConfigValidator(),
            urlsConfig,
            mfaConfig
        )
    }

    private fun webFlowProperties(id: String, root: String): AuthorizationFlowConfigurationProperties {
        return AuthorizationFlowConfigurationProperties(id).apply {
            type = "web"
            this.root = root
            signIn = "/sign-in"
            collectClaims = "/claims/edit"
            validateClaims = "/claims/validate"
            error = "/error"
        }
    }

    @Test
    fun `provideAuthorizationFlows - Serve the bundled flow under the deployment root`() {
        val factory = factory(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))

        val result = factory.provideAuthorizationFlows(emptyList())

        assertInstanceOf(EnabledAuthorizationFlowsConfig::class.java, result)
        val bundledFlow = (result as EnabledAuthorizationFlowsConfig).bundledFlow
        assertEquals(DEFAULT_WEB_AUTHORIZATION_FLOW_ID, bundledFlow.id)
        assertEquals(URI.create("https://auth.example.com/flow/sign-in"), bundledFlow.signInUri)
        assertEquals(URI.create("https://auth.example.com/flow/error"), bundledFlow.errorUri)
        assertSame(bundledFlow, result.flows.first())
    }

    @Test
    fun `provideAuthorizationFlows - Serve the configured flows beside the bundled one`() {
        val factory = factory(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))

        val result = factory.provideAuthorizationFlows(
            listOf(webFlowProperties(id = "custom", root = "https://pages.example.com"))
        )

        assertInstanceOf(EnabledAuthorizationFlowsConfig::class.java, result)
        val flows = (result as EnabledAuthorizationFlowsConfig).flows
        assertEquals(listOf(DEFAULT_WEB_AUTHORIZATION_FLOW_ID, "custom"), flows.map { it.id })
        val configuredFlow = flows.last() as InteractiveFlow
        assertEquals(URI.create("https://pages.example.com/sign-in"), configuredFlow.signInUri)
    }

    @Test
    fun `provideAuthorizationFlows - Serve no flow when the deployment root is unusable`() {
        val factory = factory(DisabledUrlsConfig(emptyList()))

        val result = factory.provideAuthorizationFlows(emptyList())

        assertInstanceOf(DisabledAuthorizationFlowsConfig::class.java, result)
    }

    @Test
    fun `provideAuthorizationFlows - Offer the reset pages a flow names all of`() {
        val factory = factory(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))
        val properties = webFlowProperties(id = "custom", root = "https://pages.example.com").apply {
            resetPassword = "/reset"
            resetPasswordValidate = "/reset/validate"
            resetPasswordChoose = "/reset/choose"
        }

        val result = factory.provideAuthorizationFlows(listOf(properties))

        val flow = (result as EnabledAuthorizationFlowsConfig).flows.last() as InteractiveFlow
        val pages = flow.resetPassword!!
        assertEquals(URI.create("https://pages.example.com/reset"), pages.resetPasswordUri)
        assertEquals(URI.create("https://pages.example.com/reset/validate"), pages.validateUri)
        assertEquals(URI.create("https://pages.example.com/reset/choose"), pages.chooseUri)
    }

    @Test
    fun `provideAuthorizationFlows - Offer no reset on a flow naming none of its pages`() {
        val factory = factory(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))

        val result = factory.provideAuthorizationFlows(
            listOf(webFlowProperties(id = "custom", root = "https://pages.example.com"))
        )

        val flows = (result as EnabledAuthorizationFlowsConfig).flows
        assertTrue(flows.all { (it as InteractiveFlow).resetPassword == null })
    }

    @Test
    fun `provideAuthorizationFlows - Refuse a flow naming some of its reset pages, naming each one missing`() {
        val factory = factory(EnabledUrlsConfig(root = URI.create("https://auth.example.com")))
        val properties = webFlowProperties(id = "custom", root = "https://pages.example.com").apply {
            resetPassword = "/reset"
        }

        val result = factory.provideAuthorizationFlows(listOf(properties))

        val errors = assertInstanceOf(DisabledAuthorizationFlowsConfig::class.java, result).configurationErrors!!
            .filterIsInstance<ConfigurationException>()
        assertEquals(
            listOf("flows.custom.reset-password-validate", "flows.custom.reset-password-choose"),
            errors.map { it.key }
        )
        assertTrue(errors.all { it.messageId == "config.flow.reset_password.missing" })
    }
}
