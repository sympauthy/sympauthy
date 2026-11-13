package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.CLIENT_FIELD_KEY
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.ClientReference
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
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.user.User
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
     * Read the page [pageParams] names of the consents the [user] has granted and not
     * revoked that [criteria] keep, each with the client whose authorization request last updated it.
     */
    suspend fun listUserConsents(
        user: User,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<ConsentWithClient> {
        val page = fields().page(
            items = consentManager.findActiveConsentsByUser(user.id),
            criteria = criteria,
            pageParams = pageParams
        )
        val clients = clientManager.getClientReferences(page.items.map(Consent::promptedByClientId))
        return page.map { ConsentWithClient(it, clients.getValue(it.promptedByClientId)) }
    }

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
            name = "prompted_by_client",
            type = ENUM,
            key = CLIENT_FIELD_KEY,
            values = clientManager.getClientFieldValues(consentManager.listPromptedByClientIds()),
            sortable = true,
            read = Consent::promptedByClientId
        )
        field("scope", STRING, key = "fields.scope", searchable = true, read = Consent::scopes)
        field("consented_at", DATE_TIME, sortable = true, read = Consent::consentedAt)
        defaultSort("consented_at")
    }

    /**
     * One consent of the collection, with the client whose authorization request last updated it.
     */
    data class ConsentWithClient(
        val consent: Consent,
        val promptedByClient: ClientReference
    )
}
