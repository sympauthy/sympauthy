package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.ObservedSecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextField
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

private const val USER_AGENT = "User-Agent"

/**
 * Reads what a request shows of where it came from, so that every surface recording a security
 * context reads the same request the same way.
 *
 * Which headers may be believed is a deployment's to say, and saying it wrong is the sharpest edge in
 * this feature: a header is read only because an operator named the proxy that writes it, and naming
 * one is a promise that the origin cannot be reached around that proxy. A deployment that has not
 * made that promise leaves the configuration empty and gets the address of the socket, which no
 * caller can choose.
 */
@Singleton
class SecurityContextManager(
    @Inject private val advancedConfig: AdvancedConfig
) {

    /**
     * What [request] shows of where it came from: the caller's address, the user agent it sent, and
     * whatever geo the proxy in front of this deployment published about it.
     *
     * Every field is null where nothing supplied it. Reading one is never a failure — an operator's
     * mistake here is a record with less in it, never a request that does not complete.
     */
    suspend fun getObservedSecurityContext(request: ObservedRequest): ObservedSecurityContext {
        val config = advancedConfig.orThrow().securityContext
        return ObservedSecurityContext(
            ip = config.read(SecurityContextField.CLIENT_IP, request),
            userAgent = request.headerOrNull(USER_AGENT),
            geo = SecurityContextGeo(
                country = config.read(SecurityContextField.COUNTRY, request),
                region = config.read(SecurityContextField.REGION, request),
                city = config.read(SecurityContextField.CITY, request)
            )
        )
    }

    /**
     * The value [field] takes on [request]: the header this deployment bound the field to, read as it
     * stands, or the configured extraction's own rule where it bound none.
     *
     * An override replaces the rule rather than standing in front of it, so a named header that did
     * not arrive answers null. Falling back would mean a field whose value cannot be traced to either
     * the header the operator named or the one the profile knows.
     */
    private fun SecurityContextConfig.read(
        field: SecurityContextField,
        request: ObservedRequest
    ): String? {
        val overriddenBy = headers[field]
        return if (overriddenBy != null) {
            request.headerOrNull(overriddenBy)
        } else {
            profile.read(field, request)
        }
    }
}
