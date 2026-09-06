package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val X_CLIENT_GEO_LOCATION = "X-Client-Geo-Location"

private const val COUNTRY_PART = 0
private const val CITY_PART = 1

/**
 * Google's external Application Load Balancer, which appends
 * `[<supplied>,]<client-ip>,<load-balancer-ip>` to `X-Forwarded-For` — so the caller is the second
 * entry from the right, and everything left of it is what the caller sent.
 *
 * Geo is a custom header the operator configures on the load balancer, in the shape Google's own
 * example gives it: `X-Client-Geo-Location:{client_region},{client_city}`. A deployment wanting
 * `client_region_subdivision` publishes it as a second custom header and names it under
 * `advanced.security-context.headers.region`, which is a plain read.
 */
@Singleton
class GcpEdgeProviderProfile : EdgeProviderProfile {

    override val name = "gcp"

    override fun clientIp(request: ObservedRequest): String? = request.forwardedForFromRight(1)

    override fun country(request: ObservedRequest): String? = request.geoLocationPart(COUNTRY_PART)

    override fun city(request: ObservedRequest): String? = request.geoLocationPart(CITY_PART)

    private fun ObservedRequest.geoLocationPart(index: Int): String? = headerOrNull(X_CLIENT_GEO_LOCATION)
        ?.split(',')
        ?.getOrNull(index)
        ?.trim()
        ?.takeUnless(String::isEmpty)
}
