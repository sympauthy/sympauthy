package com.sympauthy.api.util

import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.ObservedSecurityContext
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.orNullIfEmpty
import com.sympauthy.business.model.security.valueOrNull
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.SecurityContextGeoHeadersConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reads where a request came from, under the trust model a deployment configured — which
 * `docs/security.md` carries, including what naming a proxy promises and what it does not.
 *
 * It is a bean rather than a function because which headers may be believed is a deployment's to
 * decide, and it is in the `api` layer because a request is what it reads.
 */
@Singleton
class SecurityContextUtil(
    @Inject private val advancedConfig: AdvancedConfig,
    /**
     * Every edge publishing an address, by the name each is named with — which is the set of words
     * `advanced.security-context.ip.provider` accepts.
     */
    @Inject private val ipProviders: Map<String, IpProvider>,
    /**
     * Every edge publishing a location, which is the set `advanced.security-context.geo.providers`
     * accepts. An edge publishing none is absent from it.
     */
    @Inject private val geoProviders: Map<String, GeoProvider>
) {

    /**
     * What [request] shows about where it came from.
     *
     * The address is the header a deployment named, else the edge it named, else the peer of the
     * socket the request arrived on — one answer from one source, never a merge, because only the
     * proxy nearest this server knows it. The location is merged across the edges that answered,
     * each overriding the fields the ones before it filled, and the headers a deployment named win
     * over all of them.
     */
    fun observe(request: HttpRequest<*>): ObservedSecurityContext {
        val config = advancedConfig.orThrow().securityContext
        val headers = request.headers

        val ipAddress = config.ip.header?.let(headers::valueOrNull)
            ?: config.ip.provider?.resolve(ipProviders)?.readIpOrNull(headers)
            ?: socketPeerOf(request)

        val located = geoSources.map { it.readGeoOrNull(headers) } + namedHeaders(config.geo.headers, headers)
        val geo = located.filterNotNull().reduceOrNull { earlier, later -> earlier.mergedUnder(later) }

        return ObservedSecurityContext(
            ipAddress = ipAddress,
            userAgent = headers.valueOrNull(HttpHeaders.USER_AGENT),
            geo = geo
        )
    }

    /**
     * The edges whose location is read, weakest first, fixed once because the configuration they
     * come from is.
     *
     * Auto-detection is every edge publishing a location rather than an edge of its own, sorted so
     * that the same request is read the same way twice. Each answers only where its own headers
     * arrived, which is what makes applying all of them mean "whichever edge is in front" — and it is
     * safe here in a way it would not be for the address, because every edge publishes its location
     * under a header of its own rather than at a position in one they share.
     */
    private val geoSources: List<GeoProvider> by lazy {
        val config = advancedConfig.orThrow().securityContext.geo
        val autoDetected = if (config.autoDetect) geoProviders.toSortedMap().values else emptyList()
        autoDetected + config.providers.map { it.resolve(geoProviders) }
    }

    /**
     * Reads the location fields out of the headers a deployment named for itself, each as it stands.
     *
     * An override is never a parse: a header named here is read whole, including where it holds a
     * set of packed pairs. A deployment needing a value dug out of one names the edge that knows how
     * instead.
     */
    private fun namedHeaders(
        config: SecurityContextGeoHeadersConfig,
        headers: HttpHeaders
    ): SecurityContextGeo? = SecurityContextGeo(
        countryCode = config.countryCode?.let(headers::valueOrNull),
        regionCode = config.regionCode?.let(headers::valueOrNull),
        region = config.region?.let(headers::valueOrNull),
        city = config.city?.let(headers::valueOrNull),
        postalCode = config.postalCode?.let(headers::valueOrNull),
        timeZone = config.timeZone?.let(headers::valueOrNull)
    ).orNullIfEmpty()

    /**
     * The peer of the socket [request] arrived on, which is the caller where nothing sits in front
     * of this server and the nearest proxy where something does.
     */
    private fun socketPeerOf(request: HttpRequest<*>): String {
        val remoteAddress = request.remoteAddress
        return remoteAddress.address?.hostAddress ?: remoteAddress.hostString
    }
}
