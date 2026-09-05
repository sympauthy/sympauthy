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
}
