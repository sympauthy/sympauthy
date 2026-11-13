package com.sympauthy.util

import java.net.InetAddress

/**
 * The address [literal] writes, or null where it is not an IP address literal. The brackets around an IPv6 literal
 * are dropped, and so is its zone ID: it names an interface on one machine rather than part of the address.
 *
 * **It never resolves a name.** [InetAddress.ofLiteral] parses a literal or refuses it, where
 * [InetAddress.getByName] hands whatever it cannot parse to the platform's name service. The value may be text a
 * caller wrote, as an address header is, and resolving it would have this server look up whatever was put there.
 */
fun inetAddressOfLiteralOrNull(literal: String): InetAddress? {
    val address = literal.removeSurrounding("[", "]").substringBefore('%')
    return try {
        InetAddress.ofLiteral(address)
    } catch (_: IllegalArgumentException) {
        null
    }
}
