package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val X_AZURE_CLIENT_IP = "X-Azure-ClientIP"
private const val X_AZURE_SOCKET_IP = "X-Azure-SocketIP"

/**
 * Azure Front Door, which publishes no geo under a hardcodable name either, for the same reason as
 * [FastlyEdgeProviderProfile]: it comes out of the rules engine under names the operator chooses.
 *
 * `X-Azure-ClientIP` is the address Front Door attributes the request to, which honours an
 * `X-Forwarded-For` the caller sent; `X-Azure-SocketIP` is the peer of the TCP connection and cannot
 * be. The first is read because it is the one that is right when the deployment is closed, and the
 * second answers when it is absent.
 */
@Singleton
class AzureEdgeProviderProfile : EdgeProviderProfile {

    override val name = "azure"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(X_AZURE_CLIENT_IP)
        ?: request.headerOrNull(X_AZURE_SOCKET_IP)
}
