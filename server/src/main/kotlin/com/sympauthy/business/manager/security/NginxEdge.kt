package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
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
 * That directive is what an edge reading the same header as this one may not need, and it is why two
 * edges extracting identically are still two: an edge names the operator's world rather than a header.
 *
 * nginx publishes no location, so it implements nothing a deployment could name under the geo
 * setting.
 */
@Singleton
@Named("nginx")
class NginxEdge : IpProvider {

    override fun readIpOrNull(headers: HttpHeaders) = headers.valueOrNull(REAL_IP_HEADER)

    private companion object {

        const val REAL_IP_HEADER = "X-Real-IP"
    }
}
