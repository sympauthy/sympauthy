package com.sympauthy.business.model.securitycontext

/**
 * A request from a connection whose peer is [peer], carrying [headers] once each.
 */
fun observedRequestOf(peer: String? = null, vararg headers: Pair<String, String>) = ObservedRequest(
    peer = peer,
    headers = headers.groupBy({ it.first }, { it.second })
)
