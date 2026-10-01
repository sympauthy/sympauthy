package com.sympauthy.business.model.user

import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import java.time.LocalDateTime
import java.util.*

/**
 * An information that this authorization server collected from the user as a first party.
 *
 * We consider being first party when:
 * - the claim is collected during an authentication flow.
 * - the claim is collected by a client and stored in this authorization server.
 */
data class CollectedClaim(
    val userId: UUID,
    val claim: Claim,
    /**
     * The value of the claim.
     * It may be null if the end-user has deliberately deleted the user info.
     */
    val value: Any?,
    /**
     * Whether the value of this claim has been verified by this authorization server or by the client.
     * null if the verification is not relevant for the claim.
     */
    val verified: Boolean?,
    val collectionDate: LocalDateTime,
    /**
     * When the value has been verified by this authorization server.
     * null if the verification is not relevant for the claim or has not been verified yet.
     */
    val verificationDate: LocalDateTime?
)

/**
 * The members these claims are published as, each under the name it travels by: the claims holding a
 * value, a `<claim>_verified` companion beside each that declares one, and the claims of
 * [ClaimGroup.ADDRESS] assembled into the single `address` object OpenID Connect Core §5.1.1 defines.
 *
 * Which claims reach a place is settled before this is asked — the ACL permitted them and
 * [Claim.isPublishedIn] placed them — so what is left here is the wire form alone, written once because a
 * claim looks the same in every place that carries it.
 *
 * [reserved] is what the place already says about the authorization it belongs to — a token's subject,
 * audience, scopes and binding, or the members RFC 7662 names in an introspection response — and a claim
 * named after one of those is left out rather than written over it. It is the caller's to supply because
 * only the caller knows what it has already said; there is no default, so a new place has to answer for it.
 *
 * A claim holding no value contributes nothing, and neither does its companion: `foo_verified: true`
 * beside no `foo` is this server asserting it verified something it did not send. A value this server has
 * no wire form for contributes nothing either, and nothing is logged where it does not:
 * [CollectedClaimMapper][com.sympauthy.business.mapper.CollectedClaimMapper] refuses a row that does not
 * read back as its claim's type rather than handing one over, so a row reaching here always encodes. A
 * line written where it did not would be one per claim per token, which buries a log rather than reporting
 * anything.
 */
fun List<CollectedClaim>.publishedMembers(reserved: Set<String>): Map<String, Any> {
    val members = mutableMapOf<String, Any>()
    val (addressClaims, otherClaims) = partition { it.claim.group == ClaimGroup.ADDRESS }
    otherClaims.forEach { collectedClaim ->
        val encoded = collectedClaim.encodedValue ?: return@forEach
        members[collectedClaim.claim.id] = encoded
        collectedClaim.claim.verifiedId?.let { members[it] = collectedClaim.verified ?: false }
    }
    addressMemberOf(addressClaims)?.let { members[OpenIdConnectClaimId.ADDRESS] = it }
    // The components of the address object are not held against [reserved]: they are members of that
    // object rather than of the place, so none of them can displace anything.
    return members.filterKeys { it !in reserved }
}

/**
 * The `address` object OpenID Connect Core §5.1.1 defines, assembled from the [addressClaims] of the
 * group, or null where none of them carries a value this server can publish.
 *
 * Every member of that object is a string there, whatever type the claim behind it was declared as, so a
 * component is rendered into one rather than left out — a `postal_code` declared as a number belongs in
 * the object, and in the `formatted` line, as much as one declared as a string.
 */
private fun addressMemberOf(addressClaims: List<CollectedClaim>): Map<String, String>? {
    val address = mutableMapOf<String, String>()
    addressClaims.forEach { collectedClaim ->
        val encoded = collectedClaim.encodedValue ?: return@forEach
        address[collectedClaim.claim.id] = encoded.toString()
    }
    if (address.isEmpty()) return null
    val formatted = listOfNotNull(
        address[OpenIdConnectClaimId.STREET_ADDRESS],
        listOfNotNull(
            address[OpenIdConnectClaimId.LOCALITY],
            address[OpenIdConnectClaimId.REGION],
            address[OpenIdConnectClaimId.POSTAL_CODE]
        ).joinToString(", ").ifBlank { null },
        address[OpenIdConnectClaimId.COUNTRY]
    ).joinToString("\n").ifBlank { null }
    formatted?.let { address[OpenIdConnectClaimId.FORMATTED] = it }
    return address
}

/**
 * This claim's value as the JSON type its claim is published as, or null where it holds none and where this
 * server has no wire form for the one it holds.
 */
private val CollectedClaim.encodedValue: Any?
    get() = value?.let { encodeOrNull(claim.dataType, it) }

/**
 * [value] as the JSON type a claim of [dataType] is published as, or null where the value is not one of
 * that type after all.
 *
 * What decides the wire form is the type a deployment declared, exhaustively, and never the type the
 * value happens to be carrying. The second is an artifact of how the value round-tripped through the
 * object mapper, and reading the wire form off it is how `number` came to be absent from every id token
 * ever issued.
 *
 * The narrowing that remains is a belt-and-braces check rather than a decision: every type here is the
 * class [ClaimDataType.typeClass] names and
 * [ClaimValueMapper][com.sympauthy.business.mapper.ClaimValueMapper] reads a stored value back as, so a
 * row that reached a caller of this converted under its claim's own type already.
 */
private fun encodeOrNull(dataType: ClaimDataType, value: Any): Any? = when (dataType) {
    BOOLEAN -> value as? Boolean
    NUMBER -> (value as? Number)?.toLong()
    DATE, EMAIL, PHONE_NUMBER, STRING, TIMEZONE -> value as? String
}
