package com.sympauthy.api.util

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.ObservedSecurityContext
import com.sympauthy.business.model.security.SecurityContextGeo
import com.sympauthy.business.model.security.orNullIfEmpty
import com.sympauthy.business.model.security.valueOrNull
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextHeadersConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

/**
 * Reads where a request came from, under the trust model a deployment configured.
 *
 * It is a bean rather than a function because which headers may be believed is a deployment's to
 * decide, and it is in the `api` layer because a request is what it reads. What comes out is passed
 * on as an ordinary parameter: `docs/general-code-standard.md` keeps a manager callable without a
 * request, and a thread-local context would be intermittently absent across the coroutine boundaries
 * the managers here cross.
 *
 * **Nothing here checks that a request carrying an edge's header came from that edge.**
 * `docs/security.md` argues where that control lives and what a deployment promises by naming a
 * provider.
 */
@Singleton
class SecurityContextUtil(
    @Inject private val advancedConfig: AdvancedConfig,
    /**
     * Every edge the container publishes, by the name each is named with — which is the set of words
     * `advanced.security-context.providers` accepts.
     */
    @Inject private val edgeProviders: Map<String, EdgeProvider>
) {

    /**
     * What [request] shows about where it came from.
     *
     * The sources apply weakest first — the auto-detected providers, then the ones the deployment
     * named, then the headers it named for itself — and each overrides only the fields it answers,
     * so a source that knows the address and one that knows the location both contribute. The
     * address falls back to the peer of the socket the request arrived on, which is what a
     * deployment configuring nothing gets for every request.
     */
    fun observe(request: HttpRequest<*>): ObservedSecurityContext {
        val config = advancedConfig.orThrow().securityContext
        val headers = request.headers

        val fromEdges = sources(config)
            .fold(EdgeObservation.NONE) { observed, provider -> observed.mergedUnder(provider.read(headers)) }
        val observed = fromEdges.mergedUnder(namedHeaders(config.headers, headers))

        return ObservedSecurityContext(
            ipAddress = observed.ipAddress ?: socketPeerOf(request),
            userAgent = headers.valueOrNull(HttpHeaders.USER_AGENT),
            geo = observed.geo
        ).also(::logObservation)
    }

    /**
     * The edges to apply, weakest first.
     *
     * Auto-detection is every published provider rather than a provider of its own, sorted so that
     * the same request is read the same way twice. Each answers only where its own headers arrived,
     * which is what makes applying all of them mean "whichever edge is in front" — and where two
     * edges' headers both arrive, the later name wins, as it would in a list an operator wrote.
     * A deployment that cares which one wins writes the list.
     */
    private fun sources(config: SecurityContextConfig): List<EdgeProvider> {
        val autoDetected = if (config.autoDetect) edgeProviders.toSortedMap().values else emptyList()
        return autoDetected + config.providers.map { it.resolve(edgeProviders) }
    }

    /**
     * The fields read out of the headers a deployment named for itself, each as it stands.
     *
     * An override is never a parse: a header named here is read whole, including where it holds a
     * list or a set of packed pairs. A deployment needing a value dug out of one names the provider
     * that knows how instead.
     */
    private fun namedHeaders(config: SecurityContextHeadersConfig, headers: HttpHeaders) = EdgeObservation(
        ipAddress = config.clientIp?.let(headers::valueOrNull),
        geo = SecurityContextGeo(
            countryCode = config.countryCode?.let(headers::valueOrNull),
            regionCode = config.regionCode?.let(headers::valueOrNull),
            region = config.region?.let(headers::valueOrNull),
            city = config.city?.let(headers::valueOrNull),
            postalCode = config.postalCode?.let(headers::valueOrNull),
            timeZone = config.timeZone?.let(headers::valueOrNull)
        ).orNullIfEmpty()
    )

    /**
     * The peer of the socket [request] arrived on, which is the caller where nothing sits in front
     * of this server and the nearest proxy where something does.
     */
    private fun socketPeerOf(request: HttpRequest<*>): String {
        val remoteAddress = request.remoteAddress
        return remoteAddress.address?.hostAddress ?: remoteAddress.hostString
    }

    /**
     * Says what was observed, so that a deployment can see which of its headers is being believed
     * before anything stores what they say.
     */
    private fun logObservation(observed: ObservedSecurityContext) {
        if (!logger.isDebugEnabled) return
        logger.debug(
            "Request observed from {} ({}), user agent {}.",
            observed.ipAddress,
            observed.geo?.let { "${it.countryCode}/${it.regionCode}/${it.city}" } ?: "no location",
            observed.userAgent ?: "none"
        )
    }

    private companion object {

        val logger = LoggerFactory.getLogger(SecurityContextUtil::class.java)
    }
}
