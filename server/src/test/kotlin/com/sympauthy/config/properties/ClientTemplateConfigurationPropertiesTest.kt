package com.sympauthy.config.properties

import com.sympauthy.config.DeclaredConfigurationKeyReader
import com.sympauthy.config.DeclaredConfigurationKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientTemplateConfigurationPropertiesTest {

    private val keys = DeclaredConfigurationKeys(DeclaredConfigurationKeyReader().read())

    @Test
    fun `A display name is declared on a client and not on the template several clients share`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("clients.billing-web.display-name", "Billing portal"))
        assertEquals(
            listOf("templates.clients.default.display-name"),
            keys.findUnboundKeys("templates.clients.default.display-name", "Billing portal")
        )
    }
}
