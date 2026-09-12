package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Azure Front Door, which publishes an address and no location, for [the reason Fastly does not
 * publish one][FastlyEdge]: its location is injected through the rules engine under names the
 * operator picks, so it belongs in `advanced.security-context.geo.headers` rather than here.
 */
@Singleton
@Named("azure")
class AzureEdge : IpProvider {

    override fun readIpOrNull(headers: HttpHeaders) = headers.valueOrNull(CLIENT_IP_HEADER)

    private companion object {

        const val CLIENT_IP_HEADER = "X-Azure-ClientIP"
    }
}
