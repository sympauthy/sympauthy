package com.sympauthy.config.factory

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.DisabledAdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledAudiencesConfig
import com.sympauthy.config.parsing.AdminConfigParser
import com.sympauthy.config.properties.AdminConfigurationProperties
import com.sympauthy.config.validation.AdminConfigValidator
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    private fun factory(vararg audiences: Audience): AdminConfigFactory {
        return AdminConfigFactory(
            AdminConfigParser(parser),
            AdminConfigValidator(),
            EnabledAudiencesConfig(audiences.toList())
        )
    }

    private fun properties(
        enabled: String? = "true",
        integratedUi: String? = "true",
        audience: String? = null
    ): AdminConfigurationProperties {
        return mockk {
            every { this@mockk.enabled } returns enabled
            every { this@mockk.integratedUi } returns integratedUi
            every { this@mockk.audience } returns audience
        }
    }

    @Test
    fun `Valid config produces EnabledAdminConfig with correct audienceId`() {
        val audience = Audience(id = "admin", tokenAudience = "admin")
        val factory = factory(audience)
        val props = properties(audience = "admin")

        val result = factory.provideAdminConfig(props)

        assertInstanceOf(EnabledAdminConfig::class.java, result)
        val config = result as EnabledAdminConfig
        assertTrue(config.enabled)
        assertTrue(config.integratedUi)
        assertEquals("admin", config.audienceId)
    }

    @Test
    fun `Missing audience produces DisabledAdminConfig`() {
        val audience = Audience(id = "default", tokenAudience = "default")
        val factory = factory(audience)
        val props = properties(audience = null)

        val result = factory.provideAdminConfig(props)

        assertInstanceOf(DisabledAdminConfig::class.java, result)
        val errors = (result as DisabledAdminConfig).configurationErrors!!
        assertTrue(errors.any {
            it.message!!.contains("config.admin.audience.missing") || it.message!!.contains("config.missing")
        })
    }

    @Test
    fun `Non-existent audience produces DisabledAdminConfig`() {
        val audience = Audience(id = "default", tokenAudience = "default")
        val factory = factory(audience)
        val props = properties(audience = "admin")

        val result = factory.provideAdminConfig(props)

        assertInstanceOf(DisabledAdminConfig::class.java, result)
        val errors = (result as DisabledAdminConfig).configurationErrors!!
        assertTrue(errors.any { it.message!!.contains("config.admin.audience.not_found") })
    }
}
