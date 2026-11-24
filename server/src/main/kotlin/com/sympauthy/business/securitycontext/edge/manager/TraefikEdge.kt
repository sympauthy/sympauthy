package com.sympauthy.business.securitycontext.edge.manager

import com.sympauthy.business.securitycontext.edge.model.IpProvider
import com.sympauthy.business.securitycontext.edge.model.valueOrNull
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
