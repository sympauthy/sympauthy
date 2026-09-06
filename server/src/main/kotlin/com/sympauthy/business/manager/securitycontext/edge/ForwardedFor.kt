package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.ObservedRequest

private const val X_FORWARDED_FOR = "X-Forwarded-For"

/**
 * The entry [fromRight] places from the end of `X-Forwarded-For`, counting from zero.
 *
 * Every entry a proxy appends is that proxy's own view of its peer, so the entries at the end of the
 * list are the ones the deployment's own infrastructure wrote and the entries at the front are the
 * caller's to choose. A profile indexes from the right because it knows how many hops its provider
 * adds; a header name in configuration does not, which is why an override naming this header reads
 * the list whole and is worth nothing.
 */
internal fun ObservedRequest.forwardedForFromRight(fromRight: Int): String? = headersOf(X_FORWARDED_FOR)
    .flatMap { it.split(',') }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .let { it.getOrNull(it.size - 1 - fromRight) }
