package com.sympauthy.business.manager.security

import io.micronaut.http.HttpHeaders

/**
 * The header every proxy that forwards a request appends its own view of its peer to.
 *
 * Micronaut publishes no constant for it, and the framework's own `Forwarded` is a different header
 * that no edge here sends.
 */
internal const val X_FORWARDED_FOR = "X-Forwarded-For"

/**
 * Every entry of [X_FORWARDED_FOR], oldest first, flattened across the several headers a chain of
 * proxies may have written and the several entries each of those may hold.
 *
 * **Only an entry counted from the right is worth reading.** Each one a proxy appends is that
 * proxy's own view of the peer it accepted the connection from and cannot be forged by the caller;
 * everything to the left arrived with the request and is whatever the caller chose to send. So an
 * implementation takes the last entry, or the second from last, according to how many hops its own
 * edge adds — which is the thing a header name in a configuration file cannot know.
 */
internal fun HttpHeaders.forwardedForEntries(): List<String> = getAll(X_FORWARDED_FOR)
    .flatMap { it.split(',') }
    .mapNotNull { it.trim().ifBlank { null } }
