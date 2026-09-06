package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val CF_CONNECTING_IP = "CF-Connecting-IP"
private const val CF_IP_COUNTRY = "CF-IPCountry"
private const val CF_REGION_CODE = "cf-region-code"
private const val CF_IP_CITY = "cf-ipcity"

/**
 * Cloudflare. `CF-Connecting-IP` always arrives; `CF-IPCountry` needs IP Geolocation switched on, and
 * the finer fields need the *Add visitor location headers* managed transform, which is off until an
 * operator enables it.
 *
 * The region is `cf-region-code` rather than `cf-region`'s "Texas", so that the column holds a code
 * whichever provider filled it.
 */
@Singleton
class CloudflareEdgeProviderProfile : EdgeProviderProfile {

    override val name = "cloudflare"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(CF_CONNECTING_IP)

    override fun country(request: ObservedRequest): String? = request.headerOrNull(CF_IP_COUNTRY)

    override fun region(request: ObservedRequest): String? = request.headerOrNull(CF_REGION_CODE)

    override fun city(request: ObservedRequest): String? = request.headerOrNull(CF_IP_CITY)
}
