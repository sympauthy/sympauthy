package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf

/**
 * The word every collection publishes what it accepts under, which
 * `docs/standards/collection-standard.md` reserves: no configured item a route names by a string
 * may be identified by it, whatever its case.
 */
const val RESERVED_IDENTIFIER = "capabilities"

/**
 * Whether [identifier] is the one a capability document already answers for.
 *
 * A validator that must stop building the item asks this; every other one records the refusal
 * through [refuseReservedIdentifier] and carries on.
 */
fun isReservedIdentifier(identifier: String): Boolean =
    identifier.equals(RESERVED_IDENTIFIER, ignoreCase = true)

/**
 * Record `config.reserved_identifier` under [key] where [identifier] is the one a capability
 * document already answers for.
 */
fun ConfigParsingContext.refuseReservedIdentifier(key: String, identifier: String) {
    if (!isReservedIdentifier(identifier)) return
    addError(
        configExceptionOf(key, "config.reserved_identifier", "identifier" to identifier)
    )
}
