package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.AuthorizationCodeMapper
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.interactiveFlowSessionConcurrentModificationExceptionOf
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.util.isRowAlreadyThere
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Manager in charge of generating the authorization code that will be exchange by the client against
 * the access, refresh and id tokens.
 */
@Singleton
class AuthorizationCodeManager(
    @Inject private val authorizeCodeRepository: AuthorizationCodeRepository,
    @Inject private val authorizationCodeMapper: AuthorizationCodeMapper,
    @Inject private val randomGenerator: RandomGenerator
) {

    /**
     * Generate the authorization code a client exchanges for [session]'s tokens, in the transaction completing
     * [session], naming the [grant] its authorization opened.
     *
     * The grant is written in the same transaction, just before: a completion refused here rolls it back with
     * the code, so a session that holds one code holds one grant.
     *
     * A session holds one code: the primary key over `session_id` refuses a second, and with the code written
     * as the session completes, the request refused is one whose completion lost to a concurrent one. It
     * throws `auth.interactive_flow_session.concurrent_modification`, as the version check the loser would
     * otherwise have reached next does, so it is routed by where the winner left the session. Every other way
     * the write can be refused — the foreign key over a session that is gone — travels on as this server's own
     * failure, because nothing the client sent would have prevented it.
     */
    suspend fun generateCode(
        session: InteractiveFlowSession,
        grant: Grant
    ): AuthorizationCode {
        val entity = AuthorizationCodeEntity(
            sessionId = session.id,
            // To avoid encoded characters in the returned code.
            code = randomGenerator.generateAndEncodeToHex(),
            grantId = grant.id,
            creationDate = LocalDateTime.now(),
            // We copy the expiration to simplify the cleanup code.
            expirationDate = session.expirationDate
        )
        return try {
            authorizeCodeRepository.save(entity)
                .let(authorizationCodeMapper::toAuthorizationCode)
        } catch (failure: Exception) {
            if (!failure.isRowAlreadyThere()) {
                throw failure
            }
            throw interactiveFlowSessionConcurrentModificationExceptionOf(session)
        }
    }

    /**
     * The authorization code [session] completed with.
     *
     * Throws `code.missing`, internal, where it holds none: a session completing an authorization request is
     * given its code in the transaction completing it, so one without is this server's failure.
     */
    suspend fun fetchCode(session: InteractiveFlowSession): AuthorizationCode {
        return fetchCodeOrNull(session)
            ?: throw internalBusinessExceptionOf("code.missing", "sessionId" to session.id.toString())
    }

    /**
     * The authorization code [session] completed with, or null where it holds none — a code is collected
     * with its session, so one read while the session is being collected may find it gone.
     */
    suspend fun fetchCodeOrNull(session: InteractiveFlowSession): AuthorizationCode? {
        return authorizeCodeRepository.findById(session.id)
            ?.let(authorizationCodeMapper::toAuthorizationCode)
    }

    /**
     * Mark the authorization code [session] was handed as exchanged, and answer whether this call is the
     * one that marked it: false where a client already exchanged it, or where [session] holds no code.
     *
     * Of two calls racing over one code, exactly one answers true. The second waits on the row the first
     * wrote until the transaction holding it ends, so a caller marking the code inside the transaction
     * that issues its tokens has the second caller find those tokens committed.
     */
    suspend fun markExchanged(session: InteractiveFlowSession): Boolean {
        return authorizeCodeRepository.updateExchangeDate(
            sessionId = session.id,
            exchangeDate = LocalDateTime.now()
        ) == 1
    }
}
