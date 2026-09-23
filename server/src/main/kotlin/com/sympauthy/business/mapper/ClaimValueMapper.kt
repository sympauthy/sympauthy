package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException

@Singleton
class ClaimValueMapper(
    @Inject private val objectMapper: ObjectMapper
) {

    fun toBusiness(value: String?, claimType: ClaimDataType): Any? {
        if (value == null) {
            return null
        }
        return try {
            objectMapper.readValue(value, claimType.typeClass.java)
        } catch (_: IOException) {
            null
        }
    }

    fun toEntity(value: Any?): String? {
        return objectMapper.writeValueAsString(value)
    }

    /**
     * The spelling `collected_claims.comparison_value` holds for [value]: the value as plain text,
     * lowercased, and without the quoting [toEntity] carries.
     *
     * Plain text rather than the exchanged encoding, so that one typed thing is one identity whatever
     * type holds it — `42` under a number claim and `42` under a string one are one value here, where a
     * JSON `42` against a JSON `"42"` would make them two. The whole of what a comparison on an identifier
     * is made in, and the one definition of it. See `docs/identifier-claims.md`.
     */
    fun toComparisonValue(value: Any?): String? {
        return value?.toString()?.lowercase()
    }
}
