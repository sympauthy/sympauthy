package com.sympauthy.business.model.security

import io.micronaut.http.HttpHeaders

/**
 * An edge a request may have passed through on its way here, which a deployment names in
 * `advanced.security-context.providers` to have its headers believed.
 *
 * It is a model rather than a manager because a setting selects implementations of it, which
 * `docs/config-layer-code-standard.md` says puts it here.
 *
 * **What an implementation reads is trusted as it arrives.** Nothing checks that a request carrying
 * an edge's header actually came from that edge — `docs/security.md` argues where that check lives
 * and what naming a provider promises.
 */
interface EdgeProvider {

    /**
     * What this edge says about the request carrying [headers], each field null where the edge
     * publishes none or where the header did not arrive.
     *
     * A header that is absent is never an error: an operator who has not switched on the transform
     * that sends it, or whose proxy is configured without the directive that sets it, gets a null
     * field and a server that goes on answering.
     */
    fun read(headers: HttpHeaders): EdgeObservation
}

/**
 * The value of [name], or null where the header did not arrive or arrived empty.
 *
 * An empty header is absence rather than a value: a proxy that sets a header it has nothing to put
 * in records an empty string, and passing that on would make a field that was never known
 * indistinguishable from one an edge answered with nothing.
 *
 * The lookup is case-insensitive, which every implementation of [HttpHeaders] guarantees — so
 * Traefik's `X-Real-Ip` and nginx's conventional `X-Real-IP` are the same header and the spelling an
 * implementation writes is cosmetic. It is here rather than beside the implementations because
 * everything that reads a header for this feature reads one the same way, the deployment's own
 * overrides included.
 */
internal fun HttpHeaders.valueOrNull(name: String): String? = get(name)?.trim()?.ifBlank { null }
