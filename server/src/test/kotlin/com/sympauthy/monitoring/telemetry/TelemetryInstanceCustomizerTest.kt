package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class TelemetryInstanceCustomizerTest {

    private val customizer = TelemetryInstanceCustomizer()

    private val instanceId = UUID.randomUUID()

    @Test
    fun `identify - Names the process an instance of its own`() {
        val resource = Resource.create(Attributes.of(SERVICE_NAME, "sympauthy"))

        val identified = customizer.identify(resource, instanceId)

        assertEquals(instanceId.toString(), identified.getAttribute(SERVICE_INSTANCE_ID))
        assertEquals("sympauthy", identified.getAttribute(SERVICE_NAME))
    }

    @Test
    fun `identify - Keeps the instance the deployment named`() {
        val resource = Resource.create(Attributes.of(SERVICE_INSTANCE_ID, "pod-1"))

        val identified = customizer.identify(resource, instanceId)

        assertEquals("pod-1", identified.getAttribute(SERVICE_INSTANCE_ID))
    }

    private companion object {
        val SERVICE_NAME: AttributeKey<String> = AttributeKey.stringKey("service.name")
        val SERVICE_INSTANCE_ID: AttributeKey<String> = AttributeKey.stringKey("service.instance.id")
    }
}
