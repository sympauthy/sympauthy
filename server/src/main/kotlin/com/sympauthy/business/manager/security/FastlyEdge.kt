package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Fastly, which publishes an address and no location of its own.
 *
 * Fastly exposes a location as VCL variables — `client.geo.country_code`, `client.geo.city` — that an
 * operator injects into headers under names they choose. There is no standard name to hardcode, so
 * this edge carries the address header Fastly does publish and leaves the location to
 * `advanced.security-context.geo.headers`, where the operator names the headers they wrote.
 */
@Singleton
@Named("fastly")
class FastlyEdge : IpProvider {

    override fun readIpOrNull(headers: HttpHeaders) = headers.valueOrNull(CLIENT_IP_HEADER)

    private companion object {

        const val CLIENT_IP_HEADER = "Fastly-Client-IP"
    }
}
