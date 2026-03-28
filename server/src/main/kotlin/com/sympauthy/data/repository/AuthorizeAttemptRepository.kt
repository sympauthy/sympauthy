package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthorizeAttemptEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface AuthorizeAttemptRepository : CoroutineCrudRepository<AuthorizeAttemptEntity, UUID> {

    suspend fun findByState(state: String): AuthorizeAttemptEntity?

    @Query(
        """
        SELECT * FROM authorize_attempts AS aa
        JOIN authorization_codes AS ac ON aa.id = ac.attempt_id
        WHERE ac.code = :code
        """
    )
    suspend fun findByCode(code: String): AuthorizeAttemptEntity?

    @Query(
        """
        SELECT * FROM authorize_attempts
        WHERE expiration_date < CURRENT_TIMESTAMP
        """
    )
    suspend fun findExpired(): List<AuthorizeAttemptEntity>

    suspend fun updateProviderIdProviderNonceJsonWebTokenId(@Id id: UUID, providerId: String, providerNonceJsonWebTokenId: UUID?)

    suspend fun updateUserId(@Id id: UUID, userId: UUID)

    suspend fun updateGrantedScopes(
        @Id id: UUID,
        grantedScopes: List<String>?,
        grantedAt: LocalDateTime,
        grantedBy: String
    )

    suspend fun updateConsentedScopes(
        @Id id: UUID,
        consentedScopes: List<String>?,
        consentedAt: LocalDateTime,
        consentedBy: String
    )

    suspend fun updateError(
        @Id id: UUID,
        errorDate: LocalDateTime?,
        errorDetailsId: String?,
        errorDescriptionId: String?,
        errorValues: Map<String, String>?
    )

    suspend fun updateCompleteDate(@Id id: UUID, completeDate: LocalDateTime?)

    suspend fun updateMfaPassedDate(@Id id: UUID, mfaPassedDate: LocalDateTime)

    suspend fun deleteByIds(ids: List<UUID>): Int
}
