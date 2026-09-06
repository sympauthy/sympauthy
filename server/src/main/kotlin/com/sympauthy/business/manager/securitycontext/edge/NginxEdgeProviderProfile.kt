package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val X_REAL_IP = "X-Real-IP"

/**
 * nginx, which sets nothing until the deployment writes `proxy_set_header X-Real-IP $remote_addr;`
 * into the server block. Near-universal boilerplate, and still the operator's to write: without it
 * this profile records no address at all.
 */
@Singleton
class NginxEdgeProviderProfile : EdgeProviderProfile {

    override val name = "nginx"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(X_REAL_IP)
}
