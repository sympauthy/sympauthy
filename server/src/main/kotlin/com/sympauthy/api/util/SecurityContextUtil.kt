package com.sympauthy.api.util

import com.sympauthy.business.model.securitycontext.IpSource
import com.sympauthy.business.model.securitycontext.IpSource.NAMED_EDGE
import com.sympauthy.business.model.securitycontext.IpSource.NAMED_HEADER
import com.sympauthy.business.model.securitycontext.IpSource.SOCKET_PEER
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.business.model.securitycontext.edge.GeoProvider
import com.sympauthy.business.model.securitycontext.edge.IpProvider
import com.sympauthy.business.model.securitycontext.edge.valueOrNull
import com.sympauthy.business.model.securitycontext.orNullIfEmpty
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextGeoHeadersConfig
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reads where a request came from, under the trust model a deployment configured — which
 * `docs/design/security-context.md` carries, including what naming a proxy promises and what it does not.
 *
 * **It answers for every request and throws for none.** It is read on the whole filter chain, so a
 * failure here would be a failure of every endpoint including the one reporting why —
 * [enabledSecurityContextOrNull] is where that is held.
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
     * socket the request arrived on — one answer from one source, never a merge. The location is merged
     * across the edges that answered,
     * each overriding the fields the ones before it filled, and the headers a deployment named win
     * over all of them.
     */
    fun observe(request: HttpRequest<*>): ObservedRequest {
        val config = enabledSecurityContextOrNull()
        val headers = request.headers

        val (ipAddress, ipSource) = addressOf(request, config)
        val geo = if (readsNoLocation) null else locationOf(namedGeoHeaders(config), headers)

        return ObservedRequest(
            ipAddress = ipAddress,
            ipSource = ipSource,
            userAgent = headers.valueOrNull(HttpHeaders.USER_AGENT),
            geo = geo
        )
    }

    /**
     * The address [request] is attributed to and which answer gave it, in the order
     * `docs/design/security-context.md` fixes: a header the deployment named, then the edge it named, then the peer
     * of the socket.
     *
     * Each step answers only where the thing before it did not, so the fallback carries no
     * configuration of its own and a deployment that named nothing reaches it on the first request.
     */
    private fun addressOf(
        request: HttpRequest<*>,
        config: SecurityContextConfig?
    ): Pair<String, IpSource> {
        val headers = request.headers
        config?.ip?.header?.let(headers::valueOrNull)?.let { return it to NAMED_HEADER }
        config?.ip?.provider?.resolve(ipProviders)?.readIpOrNull(headers)?.let { return it to NAMED_EDGE }
        return socketPeerOf(request) to SOCKET_PEER
    }

    /**
     * The security-context settings, or null where the configuration did not parse, which is then
     * believed about nothing (`docs/design/security-context.md`). It narrows rather than throws, as
     * `docs/standards/config-layer-code-standard.md` has a filter do, and logs nothing: the readiness
     * listener reports the key at fault at startup.
     */
    private fun enabledSecurityContextOrNull(): SecurityContextConfig? =
        (advancedConfig as? EnabledAdvancedConfig)?.securityContext

    /**
     * The edges whose location is read, weakest first, fixed once because the configuration they
     * come from is: every edge publishing a location where auto-detection is on, sorted by name, then
     * the named ones, an edge both detected and named read in its named place
     * (`docs/design/security-context.md`).
     */
    private val geoSources: List<GeoProvider> by lazy {
        val config = enabledSecurityContextOrNull()?.geo ?: return@lazy emptyList()
        val autoDetected = if (config.autoDetect) geoProviders.toSortedMap().keys else emptySet()
        val named = config.providers.map { it.qualifier }
        (autoDetected.filterNot(named::contains) + named).map(geoProviders::getValue)
    }

    /**
     * Whether nothing would have a location read, which is what the shipped configuration has, what a
     * configuration that did not parse has, and what lets a request skip the whole of it.
     */
    private val readsNoLocation: Boolean by lazy {
        geoSources.isEmpty() && namedGeoHeaders(enabledSecurityContextOrNull()) == NO_NAMED_HEADERS
    }

    /**
     * The headers a deployment named for a location field, and none where the configuration did not
     * parse — a broken file names nothing this server may read.
     */
    private fun namedGeoHeaders(config: SecurityContextConfig?): SecurityContextGeoHeadersConfig =
        config?.geo?.headers ?: NO_NAMED_HEADERS

    /**
     * Where the edges and the headers a deployment named place the request carrying [headers], each
     * overriding the fields the ones before it answered.
     */
    private fun locationOf(
        config: SecurityContextGeoHeadersConfig,
        headers: HttpHeaders
    ): SecurityContextGeo? = (geoSources.map { it.readGeoOrNull(headers) } + namedHeaders(config, headers))
        .filterNotNull()
        .reduceOrNull { earlier, later -> earlier.mergedUnder(later) }

    /**
     * Reads the location fields out of the headers a deployment named for itself, each as it stands.
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

    private companion object {

        /**
         * A deployment that named no header of its own, which is what makes reading none of them a
         * decision this can take once.
         */
        val NO_NAMED_HEADERS = SecurityContextGeoHeadersConfig(
            countryCode = null,
            regionCode = null,
            region = null,
            city = null,
            postalCode = null,
            timeZone = null
        )
    }
}
