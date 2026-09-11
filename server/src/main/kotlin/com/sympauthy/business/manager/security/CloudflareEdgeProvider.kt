package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.orNullIfEmpty
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Cloudflare, which publishes a header per field.
 *
 * **Two switches an operator has to have thrown.** `CF-IPCountry` arrives only with IP Geolocation
 * enabled, and everything finer comes from the *Add visitor location headers* managed transform,
 * which is off until it is switched on. Neither is something this server can detect: the headers
 * simply do not arrive, the fields are null, and the deployment goes on working with an address and
 * nothing around it.
 */
@Singleton
@Named("cloudflare")
class CloudflareEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(CONNECTING_IP_HEADER),
        geo = SecurityContextGeo(
            countryCode = headers.valueOrNull(COUNTRY_HEADER),
            regionCode = headers.valueOrNull(REGION_CODE_HEADER),
            region = headers.valueOrNull(REGION_HEADER),
            city = headers.valueOrNull(CITY_HEADER),
            postalCode = headers.valueOrNull(POSTAL_CODE_HEADER),
            timeZone = headers.valueOrNull(TIME_ZONE_HEADER)
        ).orNullIfEmpty()
    )

    private companion object {

        const val CONNECTING_IP_HEADER = "CF-Connecting-IP"
        const val COUNTRY_HEADER = "CF-IPCountry"
        const val REGION_CODE_HEADER = "cf-region-code"
        const val REGION_HEADER = "cf-region"
        const val CITY_HEADER = "cf-ipcity"
        const val POSTAL_CODE_HEADER = "cf-postal-code"
        const val TIME_ZONE_HEADER = "cf-timezone"
    }
}
