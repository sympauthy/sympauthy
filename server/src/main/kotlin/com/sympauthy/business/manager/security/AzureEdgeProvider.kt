package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeObservation
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.valueOrNull
import io.micronaut.http.HttpHeaders
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Azure Front Door, which is the address only, for [the reason Fastly is][FastlyEdgeProvider]: its
 * location is injected through the rules engine under names the operator picks, so it belongs in
 * `advanced.security-context.headers` rather than here.
 */
@Singleton
@Named("azure")
class AzureEdgeProvider : EdgeProvider {

    override fun read(headers: HttpHeaders) = EdgeObservation(
        ipAddress = headers.valueOrNull(CLIENT_IP_HEADER),
        geo = null
    )

    private companion object {

        const val CLIENT_IP_HEADER = "X-Azure-ClientIP"
    }
}
