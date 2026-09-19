package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.NUMBER
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for reading the audiences this deployment configured, each with the number of
 * clients that belong to it.
 *
 * The audiences and the clients are both configuration held in memory, so counting is a grouping
 * rather than a query.
 */
@Singleton
class AudienceCollectionManager(
    @Inject private val audienceManager: AudienceManager,
    @Inject private val clientManager: ClientManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the configured audiences [criteria] keep, each with the
     * number of clients that belong to it.
     */
    suspend fun listAudiences(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<AudienceWithClientCount> {
        val clientCountsByAudienceId = clientManager.countClientsByAudienceId()
        return fields().page(
            items = audienceManager.listAudiences().map { it.withClientCount(clientCountsByAudienceId) },
            criteria = criteria,
            pageParams = pageParams
        )
    }

    /**
     * Read the audience identified by [audienceId], with the number of clients that belong to it.
     * Otherwise, return null if no audience matches.
     */
    suspend fun findAudienceByIdOrNull(audienceId: String): AudienceWithClientCount? {
        val audience = audienceManager.findAudienceByIdOrNull(audienceId) ?: return null
        return audience.withClientCount(clientManager.countClientsByAudienceId())
    }

    internal fun Audience.withClientCount(
        clientCountsByAudienceId: Map<String, Int>
    ) = AudienceWithClientCount(this, clientCountsByAudienceId[id] ?: 0)

    /**
     * The fields this collection offers, ending on the identifier, which is unique by construction —
     * and which is therefore not one a caller may order by.
     */
    private suspend fun fields(): CollectionFields<AudienceWithClientCount> = collectionFields(
        uniqueKey = compareBy { it.audience.id }
    ) {
        field("id", STRING, key = "fields.audience_id", searchable = true) { it.audience.id }
        field("token_audience", STRING, sortable = true) { it.audience.tokenAudience }
        field("sign_up_enabled", BOOLEAN, sortable = true) { it.audience.signUpEnabled }
        field("invitation_enabled", BOOLEAN, sortable = true) { it.audience.invitationEnabled }
        field("client_count", NUMBER, sortable = true) { it.clientCount }
    }

    /**
     * An audience, and the number of clients that belong to it.
     *
     * How many clients an audience groups is what an administrator reading the configuration asks
     * about it, so a collection answers with both rather than leaving its caller to count them per
     * audience.
     */
    data class AudienceWithClientCount(
        val audience: Audience,
        val clientCount: Int
    )
}
