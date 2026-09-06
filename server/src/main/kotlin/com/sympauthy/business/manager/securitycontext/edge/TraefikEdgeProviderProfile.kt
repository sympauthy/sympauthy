package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val X_REAL_IP = "X-Real-IP"

/**
 * Traefik, which populates `X-Real-Ip` itself and needs no directive.
 *
 * The extraction is [NginxEdgeProviderProfile]'s and the profile exists anyway: a profile names the
 * operator's world, and someone running Traefik should find `traefik` rather than have to know that
 * it and nginx agreed on a header.
 */
@Singleton
class TraefikEdgeProviderProfile : EdgeProviderProfile {

    override val name = "traefik"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(X_REAL_IP)
}
