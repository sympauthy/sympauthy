package com.sympauthy.business.user.manager

import com.sympauthy.business.audience.model.Audience
import com.sympauthy.business.claim.manager.CollectedClaimManager
import com.sympauthy.business.claim.model.CollectedClaim
import com.sympauthy.business.collection.model.CollectionCapabilities
import com.sympauthy.business.collection.model.CollectionCriteria
import com.sympauthy.business.collection.model.CollectionField
import com.sympauthy.business.collection.model.CollectionFieldType.STRING
import com.sympauthy.business.collection.model.CollectionOperator.EQ
import com.sympauthy.business.collection.model.Page
import com.sympauthy.business.collection.model.PageParams
import com.sympauthy.business.consent.manager.ConsentManager
import com.sympauthy.business.consent.model.Consent
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.provider.manager.ProviderClaimsManager
import com.sympauthy.business.provider.model.ProviderUserInfo
import com.sympauthy.business.user.model.ClientUser
import com.sympauthy.business.user.model.User
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
     * What this collection accepts.
     *
     * **It declares its fields without binding them to a row**, because its criteria reach the database:
     * the page, the filter and the total are each a query, and nothing here ever holds the collection to run
     * a criterion over. It therefore offers the two fields that query can answer, each under an exact match
     * and nothing else.
     *
     * Nothing publishes it, as `docs/standards/collection-standard.md` leaves the client surface.
     */
    suspend fun capabilities(): CollectionCapabilities = CollectionCapabilities(
        fields = listOf(
            CollectionField(name = "provider_id", type = STRING, key = "fields.provider_id", operators = setOf(EQ)),
            CollectionField(name = "subject", type = STRING, key = "fields.subject", operators = setOf(EQ))
        ),
        defaultSort = emptyList()
    )

    /**
     * Read the page [pageParams] names of the users who have an active consent for the given
     * [audience], oldest consent first, out of every user [criteria] keep.
     *
     * `provider_id` restricts the page to users linked to that provider, and `subject` narrows it further to
     * the account bearing it. A `subject` without a `provider_id` identifies nobody — a subject is a
     * provider's own word for a person — and throws `client.subject_without_provider`. Either field named
     * twice asks for a row holding both values, and answers an empty page.
     *
     * The page, the filter and the total are one query each, and the three batch reads that follow are also one
     * query each, so the number of round trips does not grow with [size].
     */
    suspend fun listUsersForAudience(
        audience: Audience,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<ClientUser> = coroutineScope {
        val providerIds = criteria.exactValuesOf("provider_id")
        val subjects = criteria.exactValuesOf("subject")
        if (subjects.isNotEmpty() && providerIds.isEmpty()) {
            throw recoverableBusinessExceptionOf(
                "client.subject_without_provider",
                "description.client.subject_without_provider"
            )
        }
        // Two values for one field compose with `and`, and no row holds both. The query takes one
        // value per field, so the answer is given here rather than asked of the database.
        if (providerIds.size > 1 || subjects.size > 1) {
            return@coroutineScope pageOf(emptyList(), pageParams, 0)
        }
        val providerId = providerIds.firstOrNull()
        val subject = subjects.firstOrNull()

        val deferredTotal = async {
            consentManager.countActiveConsentsByAudience(
                audience = audience,
                providerId = providerId,
                subject = subject
            )
        }
        val consents = consentManager.listActiveConsentsByAudience(
            audience = audience,
            providerId = providerId,
            subject = subject,
            page = pageParams.page,
            size = pageParams.size
        )
        if (consents.isEmpty()) {
            return@coroutineScope pageOf(emptyList(), pageParams, deferredTotal.await())
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

        pageOf(clientUsers, pageParams, deferredTotal.await())
    }

    /**
     * The page the database already sliced, which is ordered by the query rather than by a comparator.
     */
    private fun pageOf(clientUsers: List<ClientUser>, pageParams: PageParams, total: Int) = Page(
        items = clientUsers,
        page = pageParams.page,
        size = pageParams.size,
        total = total
    )

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
