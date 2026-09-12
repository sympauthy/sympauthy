package com.sympauthy.config.properties

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What a deployment writes under `advanced.security-context` reaching the properties the parser
 * reads.
 *
 * The parser's own tests hand it a properties object built in Kotlin, which answers what it does
 * with the values and nothing about whether a file produces them. The list is the half worth
 * proving: it is the only key of that shape in this domain, and a spelling the container does not
 * bind leaves the deployment with no edge named and no error saying so.
 */
class SecurityContextConfigurationPropertiesTest {

    @Test
    fun `Bind the geo providers a file lists, in the order it wrote them`() {
        withContext("advanced.security-context.geo.providers" to listOf("gcp", "cloudflare")) { context ->
            val properties = context.getBean(SecurityContextGeoConfigurationProperties::class.java)

            assertEquals(listOf("gcp", "cloudflare"), properties.providers)
        }
    }

    @Test
    fun `Bind no geo provider where the file lists none`() {
        withContext("advanced.security-context.geo.auto-detect" to "true") { context ->
            val properties = context.getBean(SecurityContextGeoConfigurationProperties::class.java)

            assertNull(properties.providers)
            assertEquals("true", properties.autoDetect)
        }
    }

    @Test
    fun `Bind the one proxy a file names for the address`() {
        withContext("advanced.security-context.ip.provider" to "nginx") { context ->
            val properties = context.getBean(SecurityContextIpConfigurationProperties::class.java)

            assertEquals("nginx", properties.provider)
            assertNull(properties.header)
        }
    }

    @Test
    fun `Bind the header a file named for one location field, and leave the others unnamed`() {
        withContext("advanced.security-context.geo.headers.city" to "X-My-Proxy-City") { context ->
            val headers = context.getBean(SecurityContextGeoHeadersConfigurationProperties::class.java)

            assertEquals("X-My-Proxy-City", headers.city)
            assertNull(headers.countryCode)
        }
    }

    /**
     * The `h2` environment is what gives the context a datasource, which starting it needs whether
     * or not this test asks anything of one.
     */
    private fun withContext(vararg properties: Pair<String, Any>, assertions: (ApplicationContext) -> Unit) {
        ApplicationContext.builder()
            .deduceEnvironment(false)
            .environments("test", "h2")
            .properties(properties.toMap())
            .build()
            .use { context ->
                context.start()
                assertions(context)
            }
    }
}
