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

    /**
     * The stored [value] of a claim of [claimType], as the type [ClaimDataType.typeClass] names, or null
     * where it does not read back as one — which is what a claim whose configured type has changed since
     * the value was written looks like from here.
     *
     * A value spelled as a JSON string reads back as the type the claim declares whenever the two can be
     * reconciled, which is what carries a boolean claim across the change that made its value a [Boolean]:
     * a row written before it holds `"true"`, and the claim a row belongs to is decided by configuration
     * rather than by anything in the database, so no migration could have found those rows to rewrite.
     */
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
}
