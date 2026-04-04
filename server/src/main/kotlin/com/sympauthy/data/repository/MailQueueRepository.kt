package com.sympauthy.data.repository

import com.sympauthy.data.model.MailQueueEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface MailQueueRepository : CoroutineCrudRepository<MailQueueEntity, UUID> {

    suspend fun findByExpirationDateIsNullOrExpirationDateAfter(
        expirationDate: LocalDateTime
    ): List<MailQueueEntity>

    suspend fun deleteByExpirationDateBefore(expirationDate: LocalDateTime): Int
}
