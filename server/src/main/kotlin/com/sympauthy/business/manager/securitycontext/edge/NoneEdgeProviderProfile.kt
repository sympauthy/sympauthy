package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import jakarta.inject.Singleton

/**
 * What a deployment that named no proxy records: the address the socket connected from, and no header
 * at all.
 *
 * This is the safe default, and it is safe because it reads nothing a caller can write. A request
 * arriving with `CF-Connecting-IP` or `X-Forwarded-For` set is recorded from its peer like any other.
 */
@Singleton
class NoneEdgeProviderProfile : EdgeProviderProfile {

    override val name = "none"

    override fun clientIp(request: ObservedRequest): String? = request.peer?.takeUnless(String::isBlank)
}
