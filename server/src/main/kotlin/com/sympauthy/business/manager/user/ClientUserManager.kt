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
     * List one page of the users holding an active consent for [audienceId], oldest consent first, and how
     * many users the same filter matches in total.
     *
     * [providerId] restricts the page to users linked to that provider, and [subject] narrows it further to
     * the single link carrying it; [subject] is only read when [providerId] is given. [page] is 0-based and
     * [size] is the number of users a page holds.
     *
     * The page is assembled by reading the consents in order and attaching what each user needs, rather
     * than by ordering what the attached reads returned: the batch reads answer for a set of users and say
     * nothing about their order.
     */
    suspend fun listUsersForAudience(
        audienceId: String,
        providerId: String?,
        subject: String?,
        page: Int,
        size: Int
    ): Pair<List<ClientUser>, Int> {
        val total = consentManager.countActiveConsentsByAudience(audienceId, providerId, subject)
        if (total == 0L) {
            return emptyList<ClientUser>() to 0
        }

        val consents = consentManager.listActiveConsentsByAudience(audienceId, providerId, subject, page, size)
        if (consents.isEmpty()) {
            return emptyList<ClientUser>() to total.toInt()
        }

        val userIds = consents.map(Consent::userId)
        val usersById = userManager.listByIds(userIds).associateBy(User::id)
        val identifierClaimsByUserId = collectedClaimManager.listIdentifierByUserIds(userIds)
            .groupBy(CollectedClaim::userId)
        val providersByUserId = providerClaimsManager.listByUserIds(userIds)
            .groupBy(ProviderUserInfo::userId)

        val clientUsers = consents.mapNotNull { consent ->
            val user = usersById[consent.userId] ?: return@mapNotNull null
            ClientUser(
                user = user,
                identifierClaims = identifierClaimsByUserId[consent.userId] ?: emptyList(),
                providers = providersByUserId[consent.userId] ?: emptyList(),
                consent = consent
            )
        }

        return clientUsers to total.toInt()
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
