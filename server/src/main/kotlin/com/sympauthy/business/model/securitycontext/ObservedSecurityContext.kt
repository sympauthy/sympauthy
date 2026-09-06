package com.sympauthy.business.model.securitycontext

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.HexFormat

private const val FINGERPRINT_DIGEST = "SHA-256"

/**
 * A separator neither half can contain, so that two observations differing only in where the address
 * ends and the user agent begins do not hash to the same place.
 */
private const val FINGERPRINT_SEPARATOR = "\n"

/**
 * What one request showed of where it came from.
 *
 * This is an observation and not a record: it carries no identity, no dates and no count, and the
 * same place seen twice produces two equal observations. What is stored, deduplicated and expired is
 * a different model.
 *
 * [ip] is null where the deployment named a proxy whose header did not arrive. It deliberately does
 * not fall back to the address the socket connected from, which under a proxy is the proxy — one
 * missing `proxy_set_header` line would otherwise record the deployment's own edge as the address
 * every person signed in from, and read as a working feature.
 */
data class ObservedSecurityContext(
    val ip: String?,
    val userAgent: String?,
    val geo: SecurityContextGeo
)

/**
 * What makes two sightings the same place: the SHA-256 of this observation's address and user agent,
 * as hexadecimal.
 *
 * It is a deduplication key and not a device fingerprint — nothing here is derived from anything the
 * caller did not send in those two values. The address is compared without regard to case, which is
 * what makes two spellings of one IPv6 address one place; the user agent is compared as it was sent,
 * since case is part of it.
 *
 * An absent half hashes as empty rather than being left out, so a caller sending no user agent is a
 * place of its own rather than the same place as every other caller sending none from another
 * address.
 */
val ObservedSecurityContext.fingerprint: String
    get() {
        val material = listOf(
            ip?.trim()?.lowercase().orEmpty(),
            userAgent?.trim().orEmpty()
        ).joinToString(FINGERPRINT_SEPARATOR)
        val digest = MessageDigest.getInstance(FINGERPRINT_DIGEST).digest(material.toByteArray(UTF_8))
        return HexFormat.of().formatHex(digest)
    }
