package com.sympauthy.business.claim.mapper

import com.sympauthy.business.claim.model.CollectedClaimUpdate
import com.sympauthy.business.claim.model.EnabledClaim
import com.sympauthy.data.model.CollectedClaimEntity
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Handle the mapping from a [CollectedClaimUpdate] to the [CollectedClaimEntity] it writes.
 *
 * **A verification is the value's and not the row's**, so both mappings write `verified` and
 * `verificationDate` as null. See `docs/design/claims.md`.
 */
@Singleton
class CollectedClaimUpdateMapper(
    private val claimValueMapper: ClaimValueMapper
) {

    fun toEntity(
        userId: UUID,
        sessionId: UUID?,
        update: CollectedClaimUpdate
    ): CollectedClaimEntity {
        return CollectedClaimEntity(
            userId = userId,
            claim = toClaim(update.claim),
            value = toValue(update.value),
            foldedEqualityHash = toFoldedEqualityHash(update.value),
            verified = null,
            collectionDate = LocalDateTime.now(),
            verificationDate = null,
            sessionId = sessionId,
        )
    }

    /**
     * Write [update] onto the row [entity] and return it, keeping the row's identifier, user and session.
     */
    fun updateEntity(
        entity: CollectedClaimEntity,
        update: CollectedClaimUpdate
    ): CollectedClaimEntity {
        entity.claim = toClaim(update.claim)
        entity.value = toValue(update.value)
        entity.foldedEqualityHash = toFoldedEqualityHash(update.value)
        entity.verified = null
        entity.verificationDate = null
        entity.collectionDate = LocalDateTime.now()
        return entity
    }

    fun toClaim(claim: EnabledClaim): String = claim.id

    fun toValue(value: Optional<Any>?): String? {
        return value?.getOrNull()?.let { claimValueMapper.toEntity(it) }
    }

    /** The [CollectedClaimEntity.foldedEqualityHash] of [value]. */
    fun toFoldedEqualityHash(value: Optional<Any>?): Long? {
        return claimValueMapper.toFoldedEqualityHash(value?.getOrNull())
    }

    /** The folded value of [value], which is what a caller re-checks a row this hash selected against. */
    fun toFoldedValue(value: Optional<Any>?): String? {
        return claimValueMapper.toFoldedValue(value?.getOrNull())
    }
}
