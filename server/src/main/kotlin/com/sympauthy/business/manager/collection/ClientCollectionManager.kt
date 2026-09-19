package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for reading the clients this deployment configured.
 *
 * The clients are configuration held in memory, so a page of them is a slice of a list rather than
 * a query.
 */
@Singleton
class ClientCollectionManager(
    @Inject private val clientManager: ClientManager,
    @Inject private val audienceManager: AudienceManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the configured clients [criteria] keep.
     */
    suspend fun listClients(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<Client> = fields().page(clientManager.listClients(), criteria, pageParams)

    /**
     * The fields this collection offers, ending on the client identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     */
    private suspend fun fields(): CollectionFields<Client> = collectionFields(
        uniqueKey = compareBy(Client::id)
    ) {
        field("id", STRING, key = "fields.client_id", searchable = true, read = Client::id)
        field(
            name = "audience_id",
            type = ENUM,
            key = "fields.audience_id",
            values = fieldValues("fields.audience_id", audienceManager.listAudiences().map(Audience::id)),
            sortable = true
        ) { it.audience.id }
        field("public", BOOLEAN, sortable = true, read = Client::public)
    }
}
