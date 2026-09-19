package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldType.UUID
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the consents a user granted.
 */
@Singleton
class ConsentCollectionManager(
    @Inject private val consentManager: ConsentManager,
    @Inject private val audienceManager: AudienceManager,
    @Inject private val clientManager: ClientManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the consents the user [userId] has granted and not
     * revoked that [criteria] keep.
     */
    suspend fun listUserConsents(
        userId: UUID,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<Consent> = fields().page(
        items = consentManager.findActiveConsentsByUser(userId),
        criteria = criteria,
        pageParams = pageParams
    )

    /**
     * The fields this collection offers, ending on the consent identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     *
     * A consent carries several scopes, so `scope` is satisfied by any of them: a caller asking for
     * the consents covering one scope means the ones holding it among the rest.
     */
    private suspend fun fields(): CollectionFields<Consent> = collectionFields(
        uniqueKey = compareBy(Consent::id)
    ) {
        field("id", UUID, key = "fields.consent_id", read = Consent::id)
        field(
            name = "audience_id",
            type = ENUM,
            key = "fields.audience_id",
            values = fieldValues("fields.audience_id", audienceManager.listAudiences().map(Audience::id)),
            sortable = true,
            read = Consent::audienceId
        )
        field(
            name = "prompted_by_client_id",
            type = ENUM,
            key = "fields.client_id",
            values = fieldValues("fields.client_id", clientManager.listClients().map(Client::id)),
            sortable = true,
            read = Consent::promptedByClientId
        )
        field("scope", STRING, key = "fields.scope", searchable = true, read = Consent::scopes)
        field("consented_at", DATE_TIME, sortable = true, read = Consent::consentedAt)
        defaultSort("consented_at")
    }
}
