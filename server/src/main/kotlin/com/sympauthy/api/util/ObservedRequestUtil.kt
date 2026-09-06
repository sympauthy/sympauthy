package com.sympauthy.api.util

import com.sympauthy.business.model.securitycontext.ObservedRequest
import io.micronaut.http.HttpRequest

/**
 * As much of this request as reading a security context from it needs.
 *
 * It is the whole of what the surface holding an [HttpRequest] contributes: which of these headers
 * may be believed, and which of them is read at all, is decided a layer below by
 * [com.sympauthy.business.manager.securitycontext.SecurityContextManager] against what the deployment
 * configured.
 */
fun HttpRequest<*>.observedRequest(): ObservedRequest = ObservedRequest(
    peer = remoteAddress.let { it.address?.hostAddress ?: it.hostString },
    headers = headers.names().associateWith(headers::getAll)
)
