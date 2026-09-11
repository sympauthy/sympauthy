package com.sympauthy.business.model.security

/**
 * Where an edge placed the address a request came from, as the edge spelled it.
 *
 * Every field is the value that arrived, unaltered and unvalidated: a country code is whatever the
 * edge calls one, a time zone may be an IANA name or an abbreviation depending on which edge sent
 * it, and a postal code may be a range where an edge publishes ranges. Normalising them would put
 * this server's opinion in front of the only thing it actually knows, which is what its edge said.
 *
 * It carries no latitude and no longitude. Everything here is a name a person could have typed;
 * a coordinate pair is a different order of precision about someone, and nothing in this server has
 * a use for one.
 */
data class SecurityContextGeo(
    val countryCode: String?,
    val regionCode: String?,
    val region: String?,
    val city: String?,
    val postalCode: String?,
    val timeZone: String?
) {

    /**
     * This location with [later] laid over it, field by field, for
     * [the reason an observation merges][EdgeObservation.mergedUnder].
     */
    fun mergedUnder(later: SecurityContextGeo?): SecurityContextGeo {
        if (later == null) return this
        return SecurityContextGeo(
            countryCode = later.countryCode ?: countryCode,
            regionCode = later.regionCode ?: regionCode,
            region = later.region ?: region,
            city = later.city ?: city,
            postalCode = later.postalCode ?: postalCode,
            timeZone = later.timeZone ?: timeZone
        )
    }

    /**
     * Whether no field was answered, which is an edge publishing geo that sent none of it rather
     * than an edge that publishes none.
     */
    val isEmpty: Boolean
        get() = countryCode == null && regionCode == null && region == null &&
            city == null && postalCode == null && timeZone == null

    companion object {

        /**
         * The location an edge that answered no field describes. [orNullIfEmpty] is what turns it
         * back into the absence it stands for, so an edge builds one field by field without having
         * to count how many it filled.
         */
        val NONE = SecurityContextGeo(
            countryCode = null, regionCode = null, region = null,
            city = null, postalCode = null, timeZone = null
        )
    }
}

/**
 * This location, or null where it holds nothing — so that an observation carries a location only
 * where something is actually known about one.
 */
fun SecurityContextGeo.orNullIfEmpty(): SecurityContextGeo? = if (isEmpty) null else this
