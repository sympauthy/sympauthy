package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface InteractiveFlowSessionOAuth2Repository :
    CoroutineCrudRepository<InteractiveFlowSessionOAuth2Entity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionOAuth2Entity?

    suspend fun findByState(state: String): InteractiveFlowSessionOAuth2Entity?

    /**
     * Write the moment a credential of the session's account was proven, and answer how many rows that
     * moved — zero where the session has no OAuth2 record.
     *
     * It answers a count where its two scope-writing neighbours answer nothing, because nothing reads
     * the record back after it: a lost write there surfaces on the next `fetchOAuth2`, and a lost write
     * here would ship an authorization whose tokens silently state no `auth_time` at all.
     */
    suspend fun updateAuthenticationDate(
        @Id sessionId: UUID,
        authenticationDate: LocalDateTime
    ): Int

    suspend fun updateConsentedScopes(
        @Id sessionId: UUID,
        consentedScopes: List<String>?,
        consentedAt: LocalDateTime,
        consentedBy: String
    )

    suspend fun updateGrantedScopes(
        @Id sessionId: UUID,
        grantedScopes: List<String>?,
        grantedAt: LocalDateTime,
        grantedBy: String
    )

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}
