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
 * Akamai, whose EdgeScape packs every field it knows into one header as comma-separated pairs:
 *
 * ```
 * X-Akamai-Edgescape: georegion=263,country_code=US,region_code=MA,city=CAMBRIDGE,zip=02138,timezone=EST
 * ```
 *
 * A `Map<field, header>` cannot express that, which is the reason a provider carries an extraction
 * rather than a table of header names.
 *
 * Two fields are what the edge sends rather than what this server would have asked for, and are
 * recorded as they arrive: `zip` may be a set of ranges rather than one code, and `timezone` is an
 * abbreviation where the other edges here send an IANA name.
 *
 * EdgeScape publishes no name for the region, only its code.
 */
@Singleton
@Named("akamai")
class AkamaiEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(TRUE_CLIENT_IP_HEADER),
        geo = headers.valueOrNull(EDGESCAPE_HEADER)?.let(::parseGeo)
    )

    /**
     * The fields [value] packs, of which this reads the five that answer something here and drops
     * the network, throughput and census fields it also carries.
     *
     * A pair without an `=` is skipped rather than refused: the header is the edge's to extend, and
     * a shape this does not recognise is not a request anybody can be told to fix.
     */
    private fun parseGeo(value: String): SecurityContextGeo? {
        val fields = value.split(',')
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator < 0) null else field.take(separator).trim() to field.substring(separator + 1).trim()
            }
            .toMap()
        return SecurityContextGeo(
            countryCode = fields[COUNTRY_CODE_FIELD]?.ifBlank { null },
            regionCode = fields[REGION_CODE_FIELD]?.ifBlank { null },
            region = null,
            city = fields[CITY_FIELD]?.ifBlank { null },
            postalCode = fields[POSTAL_CODE_FIELD]?.ifBlank { null },
            timeZone = fields[TIME_ZONE_FIELD]?.ifBlank { null }
        ).orNullIfEmpty()
    }

    private companion object {

        const val TRUE_CLIENT_IP_HEADER = "True-Client-IP"
        const val EDGESCAPE_HEADER = "X-Akamai-Edgescape"
        const val COUNTRY_CODE_FIELD = "country_code"
        const val REGION_CODE_FIELD = "region_code"
        const val CITY_FIELD = "city"
        const val POSTAL_CODE_FIELD = "zip"
        const val TIME_ZONE_FIELD = "timezone"
    }
}
