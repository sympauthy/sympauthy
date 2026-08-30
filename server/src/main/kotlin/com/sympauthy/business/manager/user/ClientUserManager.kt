package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.ClientUser
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

/**
 * Manager providing methods for the Client API to query users who have granted consent to an audience.
 */
@Singleton
class ClientUserManager(
    @Inject private val consentManager: ConsentManager,
    @Inject private val userManager: UserManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager
) {

    /**
     * List one page of the users who have an active consent for the given [audienceId], oldest consent first,
     * paired with the total number of users the filter matches.
     *
     * [providerId] restricts the page to users linked to that provider, and [subject] narrows it further to the
     * account bearing it. A [subject] without a [providerId] is refused before reaching here.
     *
     * The page, the filter and the total are one query each, and the three batch reads that follow are also one
     * query each, so the number of round trips does not grow with [size].
     */
    suspend fun listUsersForAudience(
        audienceId: String,
        providerId: String?,
        subject: String?,
        page: Int,
        size: Int
    ): Pair<List<ClientUser>, Int> = coroutineScope {
        val deferredTotal = async {
            consentManager.countActiveConsentsByAudience(
                audienceId = audienceId,
                providerId = providerId,
                subject = subject
            )
        }
        val consents = consentManager.listActiveConsentsByAudience(
            audienceId = audienceId,
            providerId = providerId,
            subject = subject,
            page = page,
            size = size
        )
        if (consents.isEmpty()) {
            return@coroutineScope emptyList<ClientUser>() to deferredTotal.await()
        }

        val userIds = consents.map(Consent::userId)
        val deferredUsers = async { userManager.listByIds(userIds) }
        val deferredIdentifierClaims = async { collectedClaimManager.listIdentifierByUserIds(userIds) }
        val deferredProviders = async { providerClaimsManager.listByUserIds(userIds) }

        val userById = deferredUsers.await().associateBy(User::id)
        val identifierClaimsByUserId = deferredIdentifierClaims.await().groupBy(CollectedClaim::userId)
        val providersByUserId = deferredProviders.await().groupBy(ProviderUserInfo::userId)

        // Mapped over the consents rather than over the user batch: an IN-list comes back in whatever
        // order the database chooses, which is the order the paging query exists to replace.
        val clientUsers = consents.mapNotNull { consent ->
            val user = userById[consent.userId] ?: return@mapNotNull null
            ClientUser(
                user = user,
                identifierClaims = identifierClaimsByUserId[user.id] ?: emptyList(),
                providers = providersByUserId[user.id] ?: emptyList(),
                consent = consent
            )
        }

        clientUsers to deferredTotal.await()
    }

    /**
     * Find a specific user if they have an active consent for the given [audienceId], or null.
     */
    suspend fun findUserForAudienceOrNull(audienceId: String, userId: UUID): ClientUser? {
        val consent = consentManager.findActiveConsentByAudienceOrNull(userId, audienceId) ?: return null
        val user = userManager.findByIdOrNull(userId) ?: return null
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(userId)
        val providers = providerClaimsManager.findByUserId(userId)

        return ClientUser(
            user = user,
            identifierClaims = identifierClaims,
            providers = providers,
            consent = consent
        )
    }
}
