package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.mapper.InteractiveFlowSessionReauthenticationMapper
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionReauthentication
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Manager in charge of the re-authentication progress attached to an [InteractiveFlowSession] by an
 * [com.sympauthy.business.model.flow.InteractiveFlowPurpose.REAUTHENTICATION] gate. It provides methods to:
 * - fetch the re-authentication record of a session.
 * - mark the account's primary credential as proven (the resolved marker of the REAUTHENTICATION purpose).
 *
 * Kept deliberately thin (repository + mapper only) so it can be injected by the purpose handler without
 * introducing a dependency cycle through the flow engine.
 */
@Singleton
class InteractiveFlowSessionReauthenticationManager(
    @Inject private val reauthenticationRepository: InteractiveFlowSessionReauthenticationRepository,
    @Inject private val reauthenticationMapper: InteractiveFlowSessionReauthenticationMapper,
) {

    /**
     * Return the [InteractiveFlowSessionReauthentication] record attached to the [session], or null if the
     * session carries no re-authentication gate (or the end-user has not proven any credential yet).
     */
    suspend fun fetchReauthenticationOrNull(session: InteractiveFlowSession): InteractiveFlowSessionReauthentication? {
        return reauthenticationRepository.findBySessionId(session.id)
            ?.let(reauthenticationMapper::toInteractiveFlowSessionReauthentication)
    }

    /**
     * Mark the account's primary credential (a password or an already-linked provider) as proven for [session],
     * resolving the REAUTHENTICATION purpose. Upserts the record since its primary key is the session id.
     */
    suspend fun markPrimaryCredentialProven(
        session: OnGoingInteractiveFlowSession
    ): InteractiveFlowSessionReauthentication {
        val entity = InteractiveFlowSessionReauthenticationEntity(
            sessionId = session.id,
            primaryCredentialProvenDate = LocalDateTime.now()
        )
        if (reauthenticationRepository.findBySessionId(session.id) != null) {
            reauthenticationRepository.update(entity)
        } else {
            reauthenticationRepository.save(entity)
        }
        return reauthenticationMapper.toInteractiveFlowSessionReauthentication(entity)
    }
}
