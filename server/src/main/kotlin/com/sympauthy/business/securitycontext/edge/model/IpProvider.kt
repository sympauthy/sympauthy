package com.sympauthy.business.securitycontext.edge.model

import io.micronaut.http.HttpHeaders

/**
 * An edge that says which address a request came from, which a deployment names in
 * `advanced.security-context.ip.provider` to have that edge's header believed.
 *
 * **A deployment names exactly one, and there is no detecting which to use**, unlike [GeoProvider]; see
 * `docs/design/security-context.md`.
 */
interface IpProvider {

    /**
     * The address this edge says the request carrying [headers] came from, or null where its header
     * did not arrive.
     *
     * A header that is absent is never an error: an operator whose proxy is configured without the
     * directive that sets it gets a null here, an address attributed to the socket peer, and a
     * server that goes on answering.
     */
    fun readIpOrNull(headers: HttpHeaders): String?
}
