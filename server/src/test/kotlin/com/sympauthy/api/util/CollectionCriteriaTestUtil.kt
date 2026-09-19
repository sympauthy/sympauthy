package com.sympauthy.api.util

import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import io.micronaut.http.HttpRequest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/**
 * A `GET` carrying [query], for a controller test that has to hand its collection a request to read
 * criteria off.
 */
fun collectionRequest(query: String = ""): HttpRequest<Any> = HttpRequest.GET(if (query.isEmpty()) "/" else "/?$query")

/**
 * A collection offering no field at all, for a controller test that is not about the criteria.
 */
fun noCapabilities() = CollectionCapabilities(fields = emptyList(), defaultSort = emptyList())

/**
 * The criteria a caller writing [filters], [sort] and [query] reaches a manager with.
 *
 * A filter is written the way it is sent — the field name, an operator appended after a dot where it
 * is not an exact match, and the value as a caller would spell it — and it goes through
 * [collectionCriteriaOf] against a request carrying it. **Reading them any other way would be a
 * second parser**, and a manager test would then pass on criteria the shipped one never produces.
 */
fun CollectionCapabilities.criteriaOf(
    vararg filters: Pair<String, String>,
    sort: String? = null,
    query: String? = null
): CollectionCriteria = collectionCriteriaOf(
    request = collectionRequest(
        filters.joinToString("&") { (parameter, value) -> "$parameter=${URLEncoder.encode(value, UTF_8)}" }
    ),
    capabilities = this,
    sort = sort,
    query = query
)
