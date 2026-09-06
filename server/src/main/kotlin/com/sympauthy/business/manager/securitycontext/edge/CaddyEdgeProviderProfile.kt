package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

/**
 * Caddy, whose `reverse_proxy` sets no `X-Real-IP` and appends the peer it saw to `X-Forwarded-For`,
 * which it does with a stock configuration. It is the one reverse-proxy profile that needs nothing of
 * its operator.
 */
@Singleton
class CaddyEdgeProviderProfile : EdgeProviderProfile {

    override val name = "caddy"

    override fun clientIp(request: ObservedRequest): String? = request.forwardedForFromRight(0)
}
