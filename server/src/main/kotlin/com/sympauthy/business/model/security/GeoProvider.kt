package com.sympauthy.business.model.security

import io.micronaut.http.HttpHeaders

/**
 * An edge that says where the address a request came from is, which a deployment names in
 * `advanced.security-context.geo.providers`.
 *
 * **Several are named where several sit in front, and they may be detected rather than named**, unlike
 * [IpProvider]; see `docs/design/security-context.md`.
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
