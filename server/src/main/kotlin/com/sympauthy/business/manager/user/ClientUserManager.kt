package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.user.ClientUser
import com.sympauthy.data.repository.ProviderUserInfoRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager providing methods for the Client API to query users who have granted scopes to a client.
 */
@Singleton
class ClientUserManager(
    @Inject private val consentManager: ConsentManager,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val providerUserInfoRepository: ProviderUserInfoRepository
) {

    /**
     * List users who have active consents for the given [clientId].
     * Optionally filters by [providerId] and [subject].
     *
     * Returns a pair of (paginated users, total count).
     */
    suspend fun listUsersForClient(
        clientId: String,
        providerId: String?,
        subject: String?,
        page: Int,
        size: Int
    ): Pair<List<ClientUser>, Int> {
        val consents = consentManager.findActiveConsentsByClient(clientId)
        if (consents.isEmpty()) {
            return emptyList<ClientUser>() to 0
        }

        val userIds = consents.map { it.userId }
        val consentByUserId = consents.associateBy { it.userId }

        // Load providers for all users
        val providersByUserId = providerUserInfoRepository.findByUserIdInList(userIds)
            .groupBy { it.id.userId }

        // Filter by provider_id and subject if specified
        val filteredUserIds = if (providerId != null) {
            providersByUserId.entries
                .filter { (_, providers) ->
                    providers.any { provider ->
                        provider.id.providerId == providerId &&
                            (subject == null || provider.subject == subject)
                    }
                }
                .map { it.key }
        } else {
            userIds
        }

        val total = filteredUserIds.size

        // Paginate
        val pagedUserIds = filteredUserIds
            .drop(page * size)
            .take(size)

        if (pagedUserIds.isEmpty()) {
            return emptyList<ClientUser>() to total
        }

        // Load users and identifier claims for the page
        val users = pagedUserIds.mapNotNull { userManager.findByIdOrNull(it) }
        val identifierClaimsByUserId = pagedUserIds.associateWith { userId ->
            collectedClaimManager.findIdentifierByUserId(userId)
        }

        val clientUsers = users.mapNotNull { user ->
            val consent = consentByUserId[user.id] ?: return@mapNotNull null
            ClientUser(
                user = user,
                identifierClaims = identifierClaimsByUserId[user.id] ?: emptyList(),
                providers = providersByUserId[user.id] ?: emptyList(),
                consent = consent
            )
        }

        return clientUsers to total
    }

    /**
     * Find a specific user if they have an active consent for the given [clientId], or null.
     */
    suspend fun findUserForClientOrNull(clientId: String, userId: UUID): ClientUser? {
        val consent = consentManager.findActiveConsentOrNull(userId, clientId) ?: return null
        val user = userManager.findByIdOrNull(userId) ?: return null
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(userId)
        val providers = providerUserInfoRepository.findByUserId(userId)

        return ClientUser(
            user = user,
            identifierClaims = identifierClaims,
            providers = providers,
            consent = consent
        )
    }
}
