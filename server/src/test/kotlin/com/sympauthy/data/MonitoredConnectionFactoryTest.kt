package com.sympauthy.data

import io.mockk.mockk
import io.r2dbc.proxy.ProxyConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MonitoredConnectionFactoryTest {

    @Test
    fun `driverConnectionFactory - Reaches the driver's factory through every factory wrapped in front of it`() {
        val driver = ConnectionFactories.get("r2dbc:h2:mem:///monitored-connection-factory-test")
        val wrapped = MonitoredConnectionFactory(ProxyConnectionFactory.builder(driver).build(), mockk())

        assertSame(driver, wrapped.driverConnectionFactory)
    }
}
