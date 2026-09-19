package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminCollectionFilterResource
import com.sympauthy.api.resource.admin.AdminCollectionFilterValueResource
import com.sympauthy.api.resource.admin.AdminCollectionSearchResource
import com.sympauthy.api.resource.admin.AdminCollectionSortResource
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.CollectionSortKey
import com.sympauthy.server.AdminMessages
import com.sympauthy.server.DisplayMessages
import com.sympauthy.util.wireName
import io.micronaut.context.MessageSource
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale

/**
 * Renders what a collection accepts into the document it publishes about itself.
 *
 * It is the one mapper every collection's capability endpoint goes through, so a field a collection adds
 * reaches a caller without anything being written here.
 */
@Singleton
class AdminCollectionCapabilitiesResourceMapper(
    @Inject @param:AdminMessages private val adminMessageSource: MessageSource,
    @Inject @param:DisplayMessages private val displayMessageSource: MessageSource
) {

    fun toResource(
        capabilities: CollectionCapabilities,
        locale: Locale
    ) = AdminCollectionCapabilitiesResource(
        search = capabilities.searchFields
            .takeIf(List<CollectionField>::isNotEmpty)
            ?.let { fields -> AdminCollectionSearchResource(fields.map(CollectionField::name)) },
        filters = capabilities.filterFields.map { toFilterResource(it, locale) },
        sorts = capabilities.sortFields.map {
            AdminCollectionSortResource(field = it.name, name = nameOf(it, locale))
        },
        defaultSort = capabilities.defaultSort
            .takeIf(List<CollectionSortKey>::isNotEmpty)
            ?.joinToString(",", transform = CollectionSortKey::spelling)
    )

    private fun toFilterResource(
        field: CollectionField,
        locale: Locale
    ) = AdminCollectionFilterResource(
        field = field.name,
        name = nameOf(field, locale),
        type = field.type.wireName,
        operators = field.operators.map(CollectionOperator::wireName),
        values = field.values?.map {
            AdminCollectionFilterValueResource(value = it.value, name = nameOf(it, locale))
        }
    )

    private fun nameOf(field: CollectionField, locale: Locale): String =
        nameOf("${field.key}.name", field.name, locale)

    private fun nameOf(value: CollectionFieldValue, locale: Locale): String =
        nameOf("${value.key}.name", value.defaultName, locale)

    /**
     * The name [key] is read under, in [locale], falling back to [default].
     *
     * **The administrator's bundle is read first and the display one after it**, because a field that
     * is something this deployment configured is named by that thing's own key — a claim by
     * `claims.<id>.name`, which is the name an end-user is shown for it — and a deployment that named
     * a claim once has named it here too.
     */
    private fun nameOf(key: String, default: String, locale: Locale): String = adminMessageSource
        .getMessage(key, locale)
        .orElseGet { displayMessageSource.getMessage(key, default, locale) }
}
