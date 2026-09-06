package com.sympauthy.business.model.securitycontext

/**
 * As much of a request as reading a security context from it needs: the address the socket connected
 * from, and the headers the caller and the proxies before it wrote.
 *
 * It exists so that reading a security context is a rule of this layer rather than of the one holding
 * the request — a manager and every profile behind it stay callable from a scheduled job, a repair
 * script and a unit test.
 *
 * A header is matched without regard to the case it was written in, as it is on the wire, and one
 * that arrived several times keeps every value in the order they arrived.
 */
class ObservedRequest(
    /**
     * The address the socket connected from, which under a proxy is the proxy.
     */
    val peer: String?,
    headers: Map<String, List<String>>
) {
    private val valuesByName: Map<String, List<String>> = headers.entries
        .associate { (name, values) -> name.lowercase() to values }

    /**
     * The value the header [name] carries, or null where it is absent or holds nothing.
     *
     * A header present and empty is a value the proxy did not have, and a record of where someone
     * signed in has no room for the difference between that and a header it never sent. Where the
     * header arrived several times this is the first value, as reading one header is on the wire.
     */
    fun headerOrNull(name: String): String? = headersOf(name).firstOrNull()
        ?.trim()
        ?.takeUnless(String::isEmpty)

    /**
     * Every value the header [name] carries, in the order they arrived, across the lines it arrived
     * on.
     */
    fun headersOf(name: String): List<String> = valuesByName[name.lowercase()].orEmpty()
}
