package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthorizeAttemptEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
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
        WHERE expiration_date < now() at time zone 'utc'
        """
    )
    suspend fun findExpired(): List<AuthorizeAttemptEntity>

    suspend fun updateUserId(@Id id: UUID, userId: UUID)

    suspend fun updateGrantedScopes(@Id id: UUID, grantedScopes: List<String>?)

    suspend fun deleteByIds(ids: List<UUID>): Int
}
