package com.sympauthy.business.model.security

import java.net.InetAddress
import java.security.MessageDigest
import java.util.HexFormat

/**
 * What makes two sightings of one person the same place: the address and the user agent, spelled the
 * way this server keys on them, and the digest of the pair that is stored beside them.
 *
 * The geo fields are deliberately not in here. They are derived from [ip] rather than part of what
 * identifies it, so folding them in would mint a new place every time an edge's city database moved.
 */
data class SecurityContextKey(
    /** The address, canonical where it is a literal and as it arrived where it is not. */
    val ip: String,
    /** What the caller claimed, bounded to [MAX_USER_AGENT_LENGTH]. */
    val userAgent: String?,
    val fingerprint: String
)

/**
 * How this observation is keyed, which is what the deduplication in `user_security_contexts` matches on.
 *
 * **The address is canonicalised, and that is load-bearing rather than tidy.** `SecurityContextUtil`
 * answers with the socket peer's rendering on one request and an edge's header text on the next, so one
 * caller through one proxy can present two spellings of one address; keyed on the raw text, the
 * deduplication silently stops matching, every sign-in inserts a row, `observation_count` lies, and
 * whatever comes to review an unfamiliar place reviews one the person has been signing in from all
 * along.
 *
 * **Both halves are bounded before they are hashed, not after.** What comes out is what gets stored, so
 * bounding afterwards would leave a row whose own key cannot be recomputed from it — and an address this
 * could not canonicalise is header text, so leaving either unbounded lets whoever is being recorded
 * choose how wide the row is and mint a fresh place on every sign-in by varying it.
 *
 * **The user agent is bounded before it is hashed, not after.** [SecurityContextKey.userAgent] is what
 * gets stored, so bounding it afterwards would leave a row whose own key cannot be recomputed from it —
 * and leaving it unbounded lets whoever is being recorded choose how wide the row is.
 */
fun ObservedRequest.securityContextKey(): SecurityContextKey {
    val canonicalIp = canonicalAddressOrNull(ipAddress) ?: ipAddress.trim().take(MAX_IP_LENGTH)
    val boundedUserAgent = userAgent?.take(MAX_USER_AGENT_LENGTH)
    return SecurityContextKey(
        ip = canonicalIp,
        userAgent = boundedUserAgent,
        fingerprint = fingerprintOf(canonicalIp, boundedUserAgent)
    )
}

/**
 * The SHA-256 of the pair, framed so that neither half can be made to look like the other.
 *
 * A user agent may hold anything a caller types, including whichever separator was chosen — so the
 * halves are divided by a byte HTTP forbids in a field value, and an absent user agent is marked
 * differently from an empty one rather than being indistinguishable from it.
 */
private fun fingerprintOf(ip: String, userAgent: String?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(ip.toByteArray())
    digest.update(FIELD_SEPARATOR)
    digest.update(if (userAgent == null) USER_AGENT_ABSENT else USER_AGENT_PRESENT)
    userAgent?.let { digest.update(it.toByteArray()) }
    return HexFormat.of().formatHex(digest.digest())
}

/**
 * [address] rendered the one way this server spells it, or null where it is not an address literal.
 *
 * **It never resolves a name, and that is the point of doing the parsing by hand.** The value may be
 * the contents of a header, which `docs/security.md` states is the caller's to write wherever a proxy is
 * named and the origin stays reachable — so handing it to a resolver would have this server look up
 * whatever a caller put there, once per sign-in, from inside the flow. Only what is already a literal is
 * canonicalised; anything else is keyed as it stands, which costs a duplicate row at worst.
 */
private fun canonicalAddressOrNull(address: String): String? {
    val trimmed = address.trim().removeSurrounding("[", "]")
    val bytes = when {
        trimmed.contains(':') -> ipv6BytesOrNull(trimmed)
        else -> ipv4BytesOrNull(trimmed)
    } ?: return null
    return InetAddress.getByAddress(bytes).hostAddress
}

/**
 * The four octets of an IPv4 literal, or null for anything else — including a name made only of digits
 * and dots, which a resolver would have gone looking for.
 */
private fun ipv4BytesOrNull(address: String): ByteArray? {
    val groups = address.split('.')
    if (groups.size != 4) return null
    val octets = groups.map { group ->
        if (group.isEmpty() || group.length > 3 || !group.all(Char::isDigit)) return null
        group.toInt().also { if (it > 255) return null }
    }
    return ByteArray(4) { octets[it].toByte() }
}

/**
 * The sixteen bytes of an IPv6 literal, or null for anything else.
 *
 * **It is spelled out here rather than delegated, because delegating it resolves names.**
 * `InetAddress.getByName` parses a literal only when the first character is a hex digit or a colon, and
 * hands everything else to the platform's name service — so a value like `.:1`, which is nothing but
 * hex digits, colons and dots, reaches a resolver. That value can be the contents of a header, which
 * `docs/security.md` states is the caller's to write wherever a proxy is named and the origin stays
 * reachable: a blocking lookup of somebody's choosing, once per sign-in, from inside the flow.
 */
private fun ipv6BytesOrNull(address: String): ByteArray? {
    // The scope id names an interface on this machine rather than part of the address, so it is dropped.
    val literal = address.substringBefore('%')
    val compressedAt = literal.indexOf(COMPRESSION)
    if (compressedAt >= 0 && literal.indexOf(COMPRESSION, compressedAt + COMPRESSION.length) >= 0) {
        return null
    }

    val leading = groupBytesOrNull(if (compressedAt >= 0) literal.take(compressedAt) else literal)
        ?: return null
    val trailing = groupBytesOrNull(if (compressedAt >= 0) literal.drop(compressedAt + COMPRESSION.length) else "")
        ?: return null

    if (compressedAt < 0) {
        return if (leading.size == ADDRESS_BYTES) leading.toByteArray() else null
    }
    // A compression stands for at least one group of zeros, so the halves have to leave room for one.
    val zeros = ADDRESS_BYTES - leading.size - trailing.size
    return if (zeros > 0) (leading + List(zeros) { ZERO } + trailing).toByteArray() else null
}

/**
 * What the colon-separated [groups] contribute, or null where any of them is not a group.
 *
 * The last may be a dotted quad, which is how the low thirty-two bits of a mapped address are written.
 */
private fun groupBytesOrNull(groups: String): List<Byte>? {
    if (groups.isEmpty()) return emptyList()
    val parts = groups.split(':')
    val bytes = mutableListOf<Byte>()
    parts.forEachIndexed { index, part ->
        if (index == parts.lastIndex && part.contains('.')) {
            bytes += (ipv4BytesOrNull(part) ?: return null).toList()
        } else {
            if (part.isEmpty() || part.length > GROUP_DIGITS) return null
            if (!part.all { it.isDigit() || it in HEX_LETTERS }) return null
            val group = part.toInt(HEX_RADIX)
            bytes += (group shr Byte.SIZE_BITS).toByte()
            bytes += group.toByte()
        }
    }
    return if (bytes.size <= ADDRESS_BYTES) bytes else null
}

/**
 * How much of a user agent is kept and keyed on. Generous for anything a browser sends, and short
 * enough that a row's width is not the caller's to choose.
 */
const val MAX_USER_AGENT_LENGTH = 1024

/**
 * How much of an address is kept where it could not be canonicalised. Longer than any literal —
 * an IPv6 address with a scope id runs to about forty-six characters — and short enough that a
 * header nobody could have meant cannot widen the row.
 */
const val MAX_IP_LENGTH = 64

private val HEX_LETTERS = ('a'..'f') + ('A'..'F')

private const val COMPRESSION = "::"

private const val ADDRESS_BYTES = 16

private const val GROUP_DIGITS = 4

private const val HEX_RADIX = 16

private const val ZERO: Byte = 0

/** A byte HTTP forbids in a field value, so neither half of the digest can contain the divider. */
private const val FIELD_SEPARATOR: Byte = 0

private const val USER_AGENT_ABSENT: Byte = 0

private const val USER_AGENT_PRESENT: Byte = 1
