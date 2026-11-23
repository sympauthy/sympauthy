package com.sympauthy.business.model.securitycontext.edge

import io.micronaut.http.HttpHeaders

/**
 * The value of [name] an edge in front of this server wrote, or null where the header did not arrive
 * or arrived empty.
 *
 * **The last value is the edge's, and any before it are the caller's**; see
 * `docs/design/security-context.md`.
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
