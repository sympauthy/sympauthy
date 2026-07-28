package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.InteractiveFlowSessionProviderMapper
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionProvider
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionProviderEntity
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager in charge of the third-party provider authorization state attached to an [InteractiveFlowSession].
 * It provides methods to:
 * - store (upsert) the provider the user is authenticating with, generating the OIDC nonce if needed.
 * - fetch the provider record of a session.
 * - reconstruct the full provider nonce JWT from the stored jti.
 */
@Singleton
class InteractiveFlowSessionProviderManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val providerRepository: InteractiveFlowSessionProviderRepository,
    @Inject private val providerMapper: InteractiveFlowSessionProviderMapper,
) {

    /**
     * Store the third-party provider the user is authenticating with, upserting the provider record of the
     * [session]. For OIDC providers, generates a signed nonce JWT bound to the session.
     * Only the random part (jti) is stored in the database; the full JWT is reconstructed on callback via
     * [buildProviderNonceOrNull].
     *
     * @return the full nonce JWT if [generateNonce] is true, null otherwise.
     */
    suspend fun setProvider(
        session: OnGoingInteractiveFlowSession,
        providerId: String,
        generateNonce: Boolean = false
    ): String? {
        val jti = if (generateNonce) UUID.randomUUID() else null
        val entity = InteractiveFlowSessionProviderEntity(
            sessionId = session.id,
            providerId = providerId,
            providerNonceJsonWebTokenId = jti
        )
        if (providerRepository.findBySessionId(session.id) != null) {
            providerRepository.update(entity)
        } else {
            providerRepository.save(entity)
        }

        return jti?.let { buildProviderNonce(session.id, it) }
    }

    /**
     * Return the [InteractiveFlowSessionProvider] record attached to the [session], or null if the user is
     * not authenticating through a third-party provider.
     */
    suspend fun fetchProviderOrNull(session: InteractiveFlowSession): InteractiveFlowSessionProvider? {
        return providerRepository.findBySessionId(session.id)
            ?.let(providerMapper::toInteractiveFlowSessionProvider)
    }

    /**
     * Reconstruct the full provider nonce JWT from the session's stored jti.
     * Returns null if the session has no provider nonce.
     */
    suspend fun buildProviderNonceOrNull(session: InteractiveFlowSession): String? {
        val jti = providerRepository.findBySessionId(session.id)?.providerNonceJsonWebTokenId ?: return null
        return buildProviderNonce(session.id, jti)
    }

    private suspend fun buildProviderNonce(sessionId: UUID, jti: UUID): String {
        return jwtManager.create(PROVIDER_NONCE_KEY_NAME) {
            subject(sessionId.toString())
            jwtID(jti.toString())
        }
    }

    companion object {
        /**
         * Name of the cryptographic key used to sign the provider nonce.
         */
        const val PROVIDER_NONCE_KEY_NAME = "provider_nonce"
    }
}
