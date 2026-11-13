package com.sympauthy.util

import io.micronaut.http.uri.UriBuilder
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

fun String?.toAbsoluteUri(
    authorizedSchemes: List<String> = listOf("http", "https")
): URI? {
    val uri = this?.let(UriBuilder::of)?.build() ?: return null
    return when {
        !authorizedSchemes.contains(uri.scheme) -> null
        uri.host.isNullOrBlank() -> null
        else -> uri
    }
}

fun mergeUri(base: URI, uri: URI): URI {
    if (uri.isAbsolute) {
        return uri
    }
    val builder = UriBuilder.of(base)
    uri.path?.let(builder::path)
    return builder.build()
}

/**
 * Whether the host of this URI is reserved for a machine or a local network, so that nothing on the internet can
 * reach it: a name of a single label, as `localhost` is, a name reserved for a local network, or a loopback, private,
 * shared or link-local address. A URI without a host is local too.
 *
 * The host is judged as it is written and never resolved: a name may well resolve to a private address inside its own
 * network and to a public one outside it.
 */
fun URI.hasLocalHost(): Boolean {
    val host = host?.lowercase()?.removeSuffix(".") ?: return true
    val address = inetAddressOfLiteralOrNull(host) ?: return '.' !in host || LOCAL_NAME_SUFFIXES.any(host::endsWith)
    return address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isShared() || address.isUniqueLocal()
}

/**
 * Whether this is an IPv4 shared address (`100.64.0.0/10`, RFC 6598), which a carrier-grade NAT or an overlay network
 * hands out behind it.
 */
private fun InetAddress.isShared(): Boolean =
    this is Inet4Address && address[0].toInt() == 100 && (address[1].toInt() and 0xc0) == 0x40

/**
 * Whether this is an IPv6 unique local address (`fc00::/7`, RFC 4193), which [InetAddress.isSiteLocalAddress] leaves
 * out: it answers only the deprecated `fec0::/10`.
 */
private fun InetAddress.isUniqueLocal(): Boolean =
    this is Inet6Address && (address[0].toInt() and 0xfe) == 0xfc

/**
 * The names reserved for a machine or a local network: `localhost` (RFC 6761), mDNS (RFC 6762), home networks
 * (RFC 8375), the top-level domain ICANN reserves for private use, and the ones a home router or a Linux host
 * customarily gives.
 */
private val LOCAL_NAME_SUFFIXES = listOf(".localhost", ".local", ".home.arpa", ".internal", ".lan", ".localdomain")
