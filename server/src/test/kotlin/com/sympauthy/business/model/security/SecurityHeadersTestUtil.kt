package com.sympauthy.business.model.security

import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.simple.SimpleHttpHeaders
import io.mockk.every
import io.mockk.mockk
import java.net.InetSocketAddress

/**
 * The proxy this server accepted the connection from, which is what a deployment naming no proxy
 * records for every request.
 */
const val SOCKET_PEER = "198.51.100.1"

/**
 * What an edge in front says the caller's address is.
 */
const val CALLER_IP = "203.0.113.7"

/**
 * An address a caller put in a header nobody asked them for, which is believed or ignored according
 * to the configuration alone.
 */
const val FORGED_IP = "192.0.2.66"

/**
 * The headers of one request, built as the framework builds them so that a case about which header
 * is believed is not also a case about how a header is looked up.
 *
 * Repeating a name adds a second value under it, which is what a chain of proxies each appending to
 * `X-Forwarded-For` produces, and what a caller sending a header their proxy appends to produces.
 */
fun headersOf(vararg values: Pair<String, String>) = SimpleHttpHeaders(ConversionService.SHARED)
    .also { headers -> values.forEach { (name, value) -> headers.add(name, value) } }

/**
 * A request whose socket peer is left unstubbed, so that a case about a header being believed fails
 * rather than passes if the address falls back to the peer instead.
 */
fun requestOf(headers: HttpHeaders): HttpRequest<*> = mockk {
    every { this@mockk.headers } returns headers
}

fun requestFromPeer(headers: HttpHeaders): HttpRequest<*> = mockk {
    every { this@mockk.headers } returns headers
    every { remoteAddress } returns InetSocketAddress(SOCKET_PEER, 443)
}

/**
 * An observation of a request, with the address and its provenance named and the rest left at what a
 * deployment reading nothing would have produced.
 *
 * A fixture rather than a mock because every field of it is read by whatever is under test, and a
 * relaxed mock answering null for the source would prove the opposite of what a case about it says.
 */
fun observedRequestOf(
    ipAddress: String = CALLER_IP,
    ipSource: IpSource = IpSource.SOCKET_PEER,
    userAgent: String? = null,
    geo: SecurityContextGeo? = null
) = ObservedRequest(
    ipAddress = ipAddress,
    ipSource = ipSource,
    userAgent = userAgent,
    geo = geo
)
