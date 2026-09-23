package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

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
     * The folded value of [value]: the value as plain text, lowercased, and without the quoting
     * [toEntity] carries. **It answers equality under folding and nothing else.**
     *
     * Plain text rather than the exchanged encoding, so that one typed thing is one identity whatever
     * type holds it — `42` under a number claim and `42` under a string one are one value here, where a
     * JSON `42` against a JSON `"42"` would make them two. The one definition of what two identifier
     * values being equal means. See `docs/identifier-claims.md`.
     */
    fun toFoldedValue(value: Any?): String? {
        return value?.toString()?.lowercase()
    }

    /**
     * The folded value a row holds, read back from the [value] column of a claim of [claimType].
     *
     * This is how a caller re-checks a row that [toFoldedEqualityHash] selected: the column carries the
     * hash and not the folded value, so the folded value is recovered from what the row publishes rather
     * than stored a second time.
     */
    fun toFoldedValueOfStored(value: String?, claimType: ClaimDataType): String? {
        return toFoldedValue(toBusiness(value, claimType))
    }

    /**
     * The `collected_claims.folded_equality_hash` of [value], or null where there is no value.
     *
     * SHA-256 rather than [Any.hashCode], and the first eight bytes of it, because the mapping has to be
     * the same in every instance and in every version: a row is found by this and by nothing else, so two
     * of them disagreeing makes an account unreachable rather than merely slow. `ClaimValueMapperTest`
     * holds it to the values it answers today.
     *
     * Eight bytes are an index and a comparison of one fixed width whatever the value's length, which is
     * the whole reason the column is not the folded value itself. They are not enough to be trusted on
     * their own — a collision is a birthday search away — so a caller acting on a row re-checks
     * [toFoldedValue] against it.
     */
    fun toFoldedEqualityHash(value: Any?): Long? {
        val folded = toFoldedValue(value) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(folded.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest).long
    }
}
