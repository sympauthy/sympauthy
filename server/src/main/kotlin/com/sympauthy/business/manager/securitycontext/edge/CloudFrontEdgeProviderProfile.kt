package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

private const val CLOUDFRONT_VIEWER_ADDRESS = "CloudFront-Viewer-Address"
private const val CLOUDFRONT_VIEWER_COUNTRY = "CloudFront-Viewer-Country"
private const val CLOUDFRONT_VIEWER_COUNTRY_REGION = "CloudFront-Viewer-Country-Region"
private const val CLOUDFRONT_VIEWER_CITY = "CloudFront-Viewer-City"

/**
 * CloudFront, which forwards none of its `CloudFront-Viewer-*` headers to the origin unless they are
 * named in the distribution's origin request policy.
 *
 * `CloudFront-Viewer-Address` carries the source port after the address — `198.51.100.10:46532` — and
 * an IPv6 address is written the same way, unbracketed, so the port is what follows the last colon
 * and the address is everything before it.
 */
@Singleton
class CloudFrontEdgeProviderProfile : EdgeProviderProfile {

    override val name = "cloudfront"

    override fun clientIp(request: ObservedRequest): String? = request.headerOrNull(CLOUDFRONT_VIEWER_ADDRESS)
        ?.substringBeforeLast(':')
        ?.takeUnless(String::isBlank)

    override fun country(request: ObservedRequest): String? = request.headerOrNull(CLOUDFRONT_VIEWER_COUNTRY)

    override fun region(request: ObservedRequest): String? = request.headerOrNull(CLOUDFRONT_VIEWER_COUNTRY_REGION)

    override fun city(request: ObservedRequest): String? = request.headerOrNull(CLOUDFRONT_VIEWER_CITY)
}
