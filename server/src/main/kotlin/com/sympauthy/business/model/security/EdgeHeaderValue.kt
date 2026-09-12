package com.sympauthy.business.model.security

import io.micronaut.http.HttpHeaders

/**
 * The value of [name] an edge in front of this server wrote, or null where the header did not arrive
 * or arrived empty.
 *
 * **The last value is the edge's, and any before it are the caller's.** A header the caller already
 * sent is one a proxy may append to rather than replace, so reading the first would read what the
 * caller chose — the same rule that makes only the rightmost entries of `X-Forwarded-For` worth
 * reading, applied to a header that arrived more than once.
 *
 * An empty value is absence rather than a value: a proxy that sets a header it has nothing to put in
 * records an empty string, and passing that on would make a field that was never known
 * indistinguishable from one an edge answered with nothing.
 *
 * The lookup is case-insensitive, which every implementation of [HttpHeaders] guarantees — so
 * Traefik's `X-Real-Ip` and nginx's conventional `X-Real-IP` are the same header and the spelling an
 * implementation writes is cosmetic.
 */
internal fun HttpHeaders.valueOrNull(name: String): String? =
    getAll(name).lastOrNull()?.trim()?.ifBlank { null }
