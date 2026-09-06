package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val FASTLY_CLIENT_IP = "Fastly-Client-IP"

/**
 * Fastly, which publishes no geo under a name anyone could hardcode — it is exposed as VCL variables
 * an operator injects under names of their own, which `headers:` is for.
 *
 * `Fastly-Client-IP` is set at the edge only where the request did not already carry one, so a caller
 * setting it themselves is believed. The deployment closes that with a VCL snippet setting the header
 * on first ingress; without it, this profile records what the caller chose.
 */
@Singleton
class FastlyEdgeProviderProfile : EdgeProviderProfile {

    override val name = "fastly"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(FASTLY_CLIENT_IP)
}
