package com.sympauthy.business.model.security

import com.sympauthy.util.inetAddressOfLiteralOrNull
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
 * [address] rendered the one way this server spells it, or null where it is not an address literal. Only what is
 * already a literal is canonicalised, and never by resolving it: the value may be the contents of a header, which
 * `docs/design/security-context.md` states is the caller's to write wherever a proxy is named and the origin stays
 * reachable. Anything else is keyed as it stands, which costs a duplicate row at worst.
 */
private fun canonicalAddressOrNull(address: String): String? = inetAddressOfLiteralOrNull(address.trim())?.hostAddress

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

/** A byte HTTP forbids in a field value, so neither half of the digest can contain the divider. */
private const val FIELD_SEPARATOR: Byte = 0

private const val USER_AGENT_ABSENT: Byte = 0

private const val USER_AGENT_PRESENT: Byte = 1
