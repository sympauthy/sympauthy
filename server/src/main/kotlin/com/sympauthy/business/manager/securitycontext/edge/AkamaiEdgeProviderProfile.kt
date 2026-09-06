package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val TRUE_CLIENT_IP = "True-Client-IP"
private const val X_AKAMAI_EDGESCAPE = "X-Akamai-Edgescape"

/**
 * Akamai, whose `True-Client-IP` needs *Send True Client IP Header* enabled on the property, and
 * whose geo needs the Content Targeting (EdgeScape) behaviour.
 *
 * `True-Client-IP` is also passed through as the caller sent it unless *Allow Clients To Set True
 * Client IP Header* is switched off, and that one is not a silent absence: it is the subject of the
 * record choosing what the record says.
 */
@Singleton
class AkamaiEdgeProviderProfile : EdgeProviderProfile {

    override val name = "akamai"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(TRUE_CLIENT_IP)

    override fun country(request: ObservedRequest): String? = request.edgescape("country_code")

    override fun region(request: ObservedRequest): String? = request.edgescape("region_code")

    override fun city(request: ObservedRequest): String? = request.edgescape("city")

    /**
     * The value [key] carries in `X-Akamai-Edgescape`, which is a comma-separated list of `key=value`
     * pairs. A field Akamai could not resolve is present with an empty value, and answers null.
     */
    private fun ObservedRequest.edgescape(key: String): String? = headerOrNull(X_AKAMAI_EDGESCAPE)
        ?.split(',')
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
        ?.takeUnless(String::isEmpty)
}
