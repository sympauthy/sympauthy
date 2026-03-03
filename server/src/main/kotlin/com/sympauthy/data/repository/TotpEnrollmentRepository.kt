package com.sympauthy.data.repository

import com.sympauthy.data.model.TotpEnrollmentEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface TotpEnrollmentRepository : CoroutineCrudRepository<TotpEnrollmentEntity, UUID> {

    suspend fun findByUserId(userId: UUID): List<TotpEnrollmentEntity>

    suspend fun findByUserIdAndConfirmedDateIsNotNull(userId: UUID): List<TotpEnrollmentEntity>

    suspend fun findByUserIdAndConfirmedDateIsNull(userId: UUID): List<TotpEnrollmentEntity>
}
