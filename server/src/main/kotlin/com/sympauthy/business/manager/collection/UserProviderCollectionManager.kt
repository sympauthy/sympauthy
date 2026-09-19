package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.config.model.EnabledProvidersConfig
import com.sympauthy.config.model.ProvidersConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for reading the third-party identity providers a user has linked.
 */
@Singleton
class UserProviderCollectionManager(
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val uncheckedProvidersConfig: ProvidersConfig
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the providers the user [userId] has linked that [criteria]
     * keep.
     */
    suspend fun listUserProviders(
        userId: UUID,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<ProviderUserInfo> = fields().page(
        items = providerClaimsManager.findByUserId(userId),
        criteria = criteria,
        pageParams = pageParams
    )

    /**
     * The fields this collection offers, ending on the provider identifier, which is unique within one
     * user by construction — and which is therefore not one a caller may order by.
     *
     * The providers are read off the configuration rather than resolved, because what a caller may
     * filter by is which providers this deployment declares; resolving one fetches its discovery
     * document, and a collection describing itself has no business doing that.
     */
    private suspend fun fields(): CollectionFields<ProviderUserInfo> = collectionFields(
        uniqueKey = compareBy(ProviderUserInfo::providerId)
    ) {
        field(
            name = "provider_id",
            type = ENUM,
            key = "fields.provider_id",
            values = configuredProviders(),
            searchable = true,
            read = ProviderUserInfo::providerId
        )
        field("link_date", DATE_TIME, sortable = true, read = ProviderUserInfo::linkDate)
        field("fetch_date", DATE_TIME, sortable = true, read = ProviderUserInfo::fetchDate)
        field("change_date", DATE_TIME, sortable = true, read = ProviderUserInfo::changeDate)
        defaultSort("link_date")
    }

    private fun configuredProviders(): List<CollectionFieldValue> =
        (uncheckedProvidersConfig as? EnabledProvidersConfig)
            ?.providers
            ?.map { CollectionFieldValue(it.id, "fields.provider_id.${it.id}", it.name) }
            .orEmpty()
}
