package com.sympauthy.platform.monitoring.telemetry.manager

import com.sympauthy.platform.monitoring.model.Outcome
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder

internal val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("outcome")
internal val ERROR_TYPE: AttributeKey<String> = AttributeKey.stringKey("error.type")
internal val CLIENT_ID: AttributeKey<String> = AttributeKey.stringKey("client_id")

/**
 * Put [outcome] under `outcome`, and its error type, if it has one, under `error.type`.
 */
internal fun AttributesBuilder.putOutcome(outcome: Outcome): AttributesBuilder {
    put(OUTCOME, outcome.value)
    outcome.errorType?.let { put(ERROR_TYPE, it) }
    return this
}

/**
 * Put [value] under [key], unless it is null: an attribute this server does not know the value of is left out
 * rather than recorded empty.
 */
internal fun AttributesBuilder.putIfNotNull(key: AttributeKey<String>, value: String?): AttributesBuilder {
    if (value != null) put(key, value)
    return this
}
