package com.sympauthy.business.model.security

/**
 * What one source said about a request, before anything decided which source to believe.
 *
 * A deployment names several edges where several sit in front of it — a cluster whose ingress knows
 * the address and whose load balancer knows the location — so an observation is partial by design
 * and is merged with the ones around it rather than replacing them.
 */
data class EdgeObservation(
    val ipAddress: String?,
    val geo: SecurityContextGeo?
) {

    /**
     * This observation with [later] laid over it, field by field, so a source at the end of the list
     * wins each field it answers and leaves the others where they were.
     *
     * Field by field rather than whole: a source answering nothing for a field never erases what an
     * earlier one answered, which is what lets `providers: [gcp, nginx]` take the address from the
     * ingress and the location from the load balancer.
     */
    fun mergedUnder(later: EdgeObservation) = EdgeObservation(
        ipAddress = later.ipAddress ?: ipAddress,
        geo = geo?.mergedUnder(later.geo) ?: later.geo
    )

    companion object {

        /**
         * An edge that said nothing, which is what a source whose headers did not arrive answers and
         * what a fold of no sources at all starts from.
         */
        val NONE = EdgeObservation(ipAddress = null, geo = null)
    }
}
