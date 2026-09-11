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
 * proving: it is the first key of that shape in this domain, and a spelling the container does not
 * bind leaves the deployment with no proxy named and no error saying so.
 */
class SecurityContextConfigurationPropertiesTest {

    @Test
    fun `Bind the providers a file lists, in the order it wrote them`() {
        withContext("advanced.security-context.providers" to listOf("gcp", "nginx")) { context ->
            val properties = context.getBean(SecurityContextConfigurationProperties::class.java)

            assertEquals(listOf("gcp", "nginx"), properties.providers)
        }
    }

    @Test
    fun `Bind no provider where the file lists none`() {
        withContext("advanced.security-context.auto-detect" to "true") { context ->
            val properties = context.getBean(SecurityContextConfigurationProperties::class.java)

            assertNull(properties.providers)
            assertEquals("true", properties.autoDetect)
        }
    }

    @Test
    fun `Bind the header a file named for one field, and leave the others unnamed`() {
        withContext("advanced.security-context.headers.city" to "X-My-Proxy-City") { context ->
            val headers = context.getBean(SecurityContextHeadersConfigurationProperties::class.java)

            assertEquals("X-My-Proxy-City", headers.city)
            assertNull(headers.clientIp)
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
