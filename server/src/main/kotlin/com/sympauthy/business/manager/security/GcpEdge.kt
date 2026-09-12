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
 * A Google Cloud external HTTP(S) load balancer.
 *
 * The address is the second entry from the right of [X_FORWARDED_FOR], which is what Google
 * documents `client_ip_address` as being: the balancer appends its own peer last, so the caller is
 * the one before it.
 *
 * **The location header is entirely the operator's**, name and format both. Google publishes no
 * location header of its own — it publishes *variables* an operator interpolates into a custom
 * header on the balancer. This edge reads the shape Google's own example produces:
 *
 * ```
 * X-Client-Geo-Location:{client_region},{client_city}
 * ```
 *
 * which yields `US,Mountain View`, so the first field is a country code and the second a city. A
 * deployment that interpolated a different set of variables, or ordered them differently, names its
 * header under `advanced.security-context.geo.headers` instead, where a value is read as it stands.
 */
@Singleton
@Named("gcp")
class GcpEdge : IpProvider, GeoProvider {

    override fun readIpOrNull(headers: HttpHeaders) =
        headers.forwardedForEntries().let { it.getOrNull(it.size - 2) }

    /**
     * The country and the city the custom header packs, positionally.
     *
     * A field beyond the second is ignored rather than guessed at: the header's shape is the
     * operator's, and reading a third position as though it meant something would put this server's
     * assumption where a deployment's configuration is.
     */
    override fun readGeoOrNull(headers: HttpHeaders): SecurityContextGeo? {
        val fields = headers.valueOrNull(GEO_LOCATION_HEADER)?.split(',')?.map(String::trim) ?: return null
        return SecurityContextGeo(
            countryCode = fields.getOrNull(0)?.ifBlank { null },
            regionCode = null,
            region = null,
            city = fields.getOrNull(1)?.ifBlank { null },
            postalCode = null,
            timeZone = null
        ).orNullIfEmpty()
    }

    private companion object {

        const val GEO_LOCATION_HEADER = "X-Client-Geo-Location"
    }
}
