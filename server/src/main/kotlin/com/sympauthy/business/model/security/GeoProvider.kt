package com.sympauthy.business.model.security

import io.micronaut.http.HttpHeaders

/**
 * An edge that says where the address a request came from is, which a deployment names in
 * `advanced.security-context.geo.providers`.
 *
 * **Several are named where several sit in front, and they may be detected rather than named.** Both
 * follow from every edge publishing its location under a name of its own — `CF-IPCountry`,
 * `X-Akamai-Edgescape` — rather than at a position in a header they share. Two edges reading their
 * own headers cannot be read at cross purposes the way [IpProvider] can, and what a wrong answer
 * costs is a wrong location on a record rather than a request attributed to whoever asked for it.
 *
 * An edge publishing no location implements nothing here, so a deployment naming one under the geo
 * setting is refused at startup rather than configured to no effect.
 */
interface GeoProvider {

    /**
     * Where this edge places the request carrying [headers], or null where it sent none of it.
     *
     * Every field is nullable on its own: an edge sends what a transform or an origin policy was
     * switched on for, and the rest simply does not arrive.
     */
    fun readGeoOrNull(headers: HttpHeaders): SecurityContextGeo?
}
