package com.sympauthy.api.util

import com.sympauthy.business.model.collection.CollectionCapabilities
import io.micronaut.http.HttpRequest

/**
 * A `GET` carrying [query], for a controller test that has to hand its collection a request to read
 * criteria off.
 */
fun collectionRequest(query: String = ""): HttpRequest<Any> = HttpRequest.GET(if (query.isEmpty()) "/" else "/?$query")

/**
 * A collection offering no field at all, for a controller test that is not about the criteria.
 */
fun noCapabilities() = CollectionCapabilities(fields = emptyList(), defaultSort = emptyList())
