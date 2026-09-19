package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf

/**
 * The word every collection publishes what it accepts under, and which no item of a collection
 * named by a string may be identified by.
 *
 * `/api/v1/admin/audiences/capabilities` answers with what that collection accepts, so an audience
 * named by that word would be unreachable through its own item route. The set of collections that
 * have one grows, and an identifier is written once and read for the life of a deployment, so it is
 * refused for every configured item a route names by string rather than for the ones that happen to
 * have an item route today. See docs/collection-standard.md.
 */
const val RESERVED_IDENTIFIER = "capabilities"

/**
 * Whether [identifier] is the one a capability document already answers for.
 *
 * A validator that must stop building the item asks this; every other one records the refusal and
 * carries on, since any configuration error takes readiness down anyway.
 */
fun isReservedIdentifier(identifier: String): Boolean =
    identifier.equals(RESERVED_IDENTIFIER, ignoreCase = true)

/**
 * Record an error under [key] where [identifier] is the one a capability document already answers
 * for.
 *
 * A deployment is refused rather than left with a row it cannot read: the identifier is what every
 * other part of the configuration references the item by, so it is cheap to change at startup and
 * not afterwards.
 */
fun ConfigParsingContext.refuseReservedIdentifier(key: String, identifier: String) {
    if (!isReservedIdentifier(identifier)) return
    addError(
        configExceptionOf(key, "config.reserved_identifier", "identifier" to identifier)
    )
}
