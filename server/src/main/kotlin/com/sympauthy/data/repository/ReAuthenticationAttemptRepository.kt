package com.sympauthy.data.repository

import com.sympauthy.data.model.ReAuthenticationAttemptEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface ReAuthenticationAttemptRepository : CoroutineCrudRepository<ReAuthenticationAttemptEntity, UUID> {

    suspend fun updatePassedDatePassedMethod(@Id id: UUID, passedDate: LocalDateTime, passedMethod: String)
}
