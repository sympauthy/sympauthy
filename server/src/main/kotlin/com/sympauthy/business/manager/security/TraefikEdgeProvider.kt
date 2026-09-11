package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * A Traefik reverse proxy, which populates `X-Real-IP` itself and needs nothing configured to do it.
 *
 * Traefik publishes no location, so a deployment behind it gets the address and the user agent.
 */
@Singleton
@Named("traefik")
class TraefikEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(REAL_IP_HEADER),
        geo = null
    )

    private companion object {

        const val REAL_IP_HEADER = "X-Real-Ip"
    }
}
