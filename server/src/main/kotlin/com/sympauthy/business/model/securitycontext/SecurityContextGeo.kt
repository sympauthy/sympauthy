package com.sympauthy.business.model.securitycontext

/**
 * Where an edge proxy placed the address a request came from.
 *
 * Every field is what the proxy in front of this deployment happened to publish, so all three are
 * absent on a deployment behind an ordinary reverse proxy — geo is never derived here, from an
 * address database or anything else.
 *
 * [region] is a code rather than a name — `TX` rather than `Texas` — whichever proxy filled it, so
 * that two rows written under different providers still compare.
 */
data class SecurityContextGeo(
    val country: String?,
    val region: String?,
    val city: String?
)
