package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Requires
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import io.opentelemetry.sdk.resources.Resource
import jakarta.inject.Singleton
import java.util.*

/**
 * Exports this process as an instance of its own, under a `service.instance.id` it draws when it starts, as
 * `docs/design/telemetry.md` says.
 *
 * Without it, every instance of a deployment exports one identical resource, and the collector merges their
 * series: a gauge reads as whichever instance exported last, and the running totals of two instances interleave
 * into one that appears to reset.
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class TelemetryInstanceCustomizer : OpenTelemetryBuilderCustomizer {

    override fun configure(builder: AutoConfiguredOpenTelemetrySdkBuilder) {
        builder.addResourceCustomizer { resource, _ -> identify(resource, UUID.randomUUID()) }
    }

    /**
     * [resource] with [instanceId] as its `service.instance.id`, unless the deployment named one in
     * `otel.resource.attributes`.
     */
    internal fun identify(resource: Resource, instanceId: UUID): Resource {
        if (resource.getAttribute(SERVICE_INSTANCE_ID) != null) return resource
        return resource.merge(Resource.create(Attributes.of(SERVICE_INSTANCE_ID, instanceId.toString())))
    }

    companion object {
        private val SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id")
    }
}
