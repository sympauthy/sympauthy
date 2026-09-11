package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Fastly, which is the address only, on purpose.
 *
 * Fastly exposes location as VCL variables — `client.geo.country_code`, `client.geo.city` — that an
 * operator injects into headers under names they choose. There is no standard name to hardcode, so
 * this provider carries the client address header Fastly does publish and leaves the location to
 * `advanced.security-context.headers`, where the operator names the headers they wrote.
 */
@Singleton
@Named("fastly")
class FastlyEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(CLIENT_IP_HEADER),
        geo = null
    )

    private companion object {

        const val CLIENT_IP_HEADER = "Fastly-Client-IP"
    }
}
