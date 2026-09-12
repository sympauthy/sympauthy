package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.orNullIfEmpty
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Amazon CloudFront.
 *
 * **CloudFront forwards none of these headers to the origin unless they are in the origin request
 * policy.** A distribution that has not listed them sends nothing, which reads here as an edge that
 * published nothing.
 *
 * The address is a parse rather than a plain read because `CloudFront-Viewer-Address` carries the
 * source port beside it — `198.51.100.10:46532` — and the port is the connection's rather than the
 * caller's.
 */
@Singleton
@Named("cloudfront")
class CloudFrontEdge : IpProvider, GeoProvider {

    override fun readIpOrNull(headers: HttpHeaders) =
        headers.valueOrNull(VIEWER_ADDRESS_HEADER)?.let(::addressWithoutPort)

    override fun readGeoOrNull(headers: HttpHeaders) = SecurityContextGeo(
        countryCode = headers.valueOrNull(COUNTRY_HEADER),
        regionCode = headers.valueOrNull(REGION_CODE_HEADER),
        region = headers.valueOrNull(REGION_HEADER),
        city = headers.valueOrNull(CITY_HEADER),
        postalCode = headers.valueOrNull(POSTAL_CODE_HEADER),
        timeZone = headers.valueOrNull(TIME_ZONE_HEADER)
    ).orNullIfEmpty()

    /**
     * [address] without the source port CloudFront appends to it.
     *
     * The port is what follows the last colon, and the brackets an IPv6 address is sometimes written
     * in are dropped with it, so what comes out is the address as every other edge here spells one.
     * An IPv4 address carrying no port is left alone, having no colon for this to find.
     *
     * **It reads the last colon as the port's because CloudFront documents the port as always being
     * there.** An IPv6 address arriving without one is mostly colons and would lose its last group,
     * which is why this is the edge's own parse: a deployment whose header holds something else
     * names it under `advanced.security-context.ip.header`, where a value is read as it stands.
     */
    private fun addressWithoutPort(address: String): String? = address
        .substringBeforeLast(':')
        .removeSurrounding("[", "]")
        .ifBlank { null }

    private companion object {

        const val VIEWER_ADDRESS_HEADER = "CloudFront-Viewer-Address"
        const val COUNTRY_HEADER = "CloudFront-Viewer-Country"
        const val REGION_CODE_HEADER = "CloudFront-Viewer-Country-Region"
        const val REGION_HEADER = "CloudFront-Viewer-Country-Region-Name"
        const val CITY_HEADER = "CloudFront-Viewer-City"
        const val POSTAL_CODE_HEADER = "CloudFront-Viewer-Postal-Code"
        const val TIME_ZONE_HEADER = "CloudFront-Viewer-Time-Zone"
    }
}
