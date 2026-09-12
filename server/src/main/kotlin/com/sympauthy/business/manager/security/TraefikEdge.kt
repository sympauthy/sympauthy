package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * A Traefik reverse proxy, which populates `X-Real-IP` itself and needs nothing configured to do it.
 *
 * Traefik publishes no location.
 */
@Singleton
@Named("traefik")
class TraefikEdge : IpProvider {

    override fun readIpOrNull(headers: HttpHeaders) = headers.valueOrNull(REAL_IP_HEADER)

    private companion object {

        const val REAL_IP_HEADER = "X-Real-Ip"
    }
}
