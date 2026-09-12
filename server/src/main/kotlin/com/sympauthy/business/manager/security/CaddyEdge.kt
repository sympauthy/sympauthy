package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * A Caddy reverse proxy, which needs nothing configured for this to work.
 *
 * Caddy's `reverse_proxy` sets no `X-Real-IP`. It appends the peer it saw to [X_FORWARDED_FOR],
 * which it does by default, so the address is the last entry of that header — the one Caddy itself
 * wrote — and never the whole list, of which everything to the left is the caller's.
 *
 * Caddy publishes no location.
 */
@Singleton
@Named("caddy")
class CaddyEdge : IpProvider {

    override fun readIpOrNull(headers: HttpHeaders) = headers.forwardedForEntries().lastOrNull()
}
