package com.sympauthy.business.model.security

import io.micronaut.http.HttpHeaders

/**
 * An edge that says which address a request came from, which a deployment names in
 * `advanced.security-context.ip.provider` to have that edge's header believed.
 *
 * It is a model rather than a manager because a setting selects an implementation of it, which
 * `docs/config-layer-code-standard.md` says puts it here.
 *
 * **A deployment names exactly one, and there is no detecting which to use.** Only the proxy nearest
 * this server knows the address as something other than a value it was handed, and an edge reading
 * an entry of `X-Forwarded-For` reads it at a position only its own hop count explains — so a server
 * guessing which edge is in front would read that position under the wrong assumption and reach an
 * entry the caller wrote. [GeoProvider] is detectable for the reasons this is not.
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
