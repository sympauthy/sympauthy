package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import io.micronaut.json.tree.JsonNode
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
     * the value was written looks like from here, and what makes
     * [CollectedClaimMapper][com.sympauthy.business.mapper.CollectedClaimMapper] drop the row.
     *
     * A boolean is read out of the JSON rather than converted into one. The object mapper converts freely
     * in that direction — `"maybe"`, `""`, `0` and `[1]` all reach it as a truth value — so a row that is
     * not a boolean would be published as a `false` this server invented, on every surface a claim reaches
     * and to a granting rule keyed on it. The two spellings below are the only ones a boolean claim has
     * ever been stored as: the string is what it held before its value became a [Boolean], and the claim a
     * row belongs to is configuration rather than a column, so no migration could have found those rows.
     */
    fun toBusiness(value: String?, claimType: ClaimDataType): Any? {
        if (value == null) {
            return null
        }
        if (claimType == BOOLEAN) {
            val node = readOrNull(value, JsonNode::class.java) ?: return null
            return when {
                node.isBoolean -> node.booleanValue
                node.isString -> node.stringValue.toBooleanStrictOrNull()
                else -> null
            }
        }
        return readOrNull(value, claimType.typeClass.java)
    }

    fun toEntity(value: Any?): String? {
        return objectMapper.writeValueAsString(value)
    }

    private fun <T : Any> readOrNull(value: String, type: Class<T>): T? = try {
        objectMapper.readValue(value, type)
    } catch (_: IOException) {
        null
    }
}
