package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * An nginx reverse proxy, and every Kubernetes ingress that is nginx underneath.
 *
 * **nginx sets nothing until someone writes the directive.** It is near-universal boilerplate, it is
 * already in most ingress templates, and it is still the operator's to write:
 *
 * ```nginx
 * proxy_set_header X-Real-IP $remote_addr;
 * ```
 *
 * Without it the header never arrives, the address falls back to the socket peer, and nothing fails.
 * That directive is what a provider reading the same header as this one may not need, and it is why
 * two providers extracting identically are still two: a provider names the operator's world rather
 * than a header.
 *
 * nginx publishes no location of its own. A deployment behind it on a private network gets the
 * address and the user agent, and that is the whole of it.
 */
@Singleton
@Named("nginx")
class NginxEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(REAL_IP_HEADER),
        geo = null
    )

    private companion object {

        const val REAL_IP_HEADER = "X-Real-IP"
    }
}
