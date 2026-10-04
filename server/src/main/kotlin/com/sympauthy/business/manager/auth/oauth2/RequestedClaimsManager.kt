package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.RequestedClaims
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reads the `claims` request parameter of OpenID Connect Core §5.5 off an authorization request.
 *
 * It is the one reader of that parameter, the way [PkceManager] is of the PKCE pair: the claims a
 * deployment configured are [ClaimManager]'s, and what a client asked for in one request is this.
 */
@Singleton
class RequestedClaimsManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val objectMapper: ObjectMapper
) {

    /**
     * Return the claims [uncheckedClaims] names per OpenID channel. A client that sent no such parameter
     * passes null and gets [RequestedClaims.NONE].
     *
     * **The answer is sanitized**, which is what lets everything downstream treat it as claim ids: a name
     * matching no claim this deployment has enabled is dropped here, so no later read re-parses a document
     * or carries a name that can fail to resolve. A name is dropped rather than refused because §5.5.1
     * forbids erroring over a claim that is not returned — a client asking for something this deployment
     * does not have gets what it does have.
     *
     * **`essential`, `value` and `values` are read past and ignored.** §5.5.1 permits exactly that: the
     * server "MUST NOT generate an error when Claims are not returned, whether they are Essential or
     * Voluntary", and an essential claim cannot make this server disclose what a deployment withheld. Only
     * the name a member is written under is read, whatever stands against it.
     *
     * Throws a non-recoverable [BusinessException] `claim.parse_requested.invalid` where
     * [uncheckedClaims] is not the JSON object §5.5 fixes the value as, or where the `id_token` or
     * `userinfo` member is present and is neither null nor an object. Those are the shapes the
     * specification settles, so a value outside them is a request this server cannot act on rather than a
     * claim it declines to return.
     */
    fun parseRequestedClaims(uncheckedClaims: String?): RequestedClaims {
        if (uncheckedClaims.isNullOrBlank()) return RequestedClaims.NONE
        val root = readObjectOrNull(uncheckedClaims) ?: throw invalidRequestedClaims()
        return RequestedClaims(
            idTokenClaimIds = namedClaimIds(root, "id_token"),
            userInfoClaimIds = namedClaimIds(root, "userinfo")
        )
    }

    /**
     * The claim ids the [member] of [root] names, kept to the claims this deployment has enabled.
     *
     * A member that is absent or null names nothing, which is a client asking for one channel and saying
     * nothing about the other. A member that is present and is not an object is the malformed case
     * [parseRequestedClaims] refuses.
     */
    private fun namedClaimIds(root: JsonNode, member: String): Set<String> {
        val requested = root.get(member)?.takeUnless(JsonNode::isNull) ?: return emptySet()
        if (!requested.isObject) throw invalidRequestedClaims()
        return requested.entries().asSequence()
            .map { it.key }
            .filter { claimManager.findByIdOrNull(it)?.enabled == true }
            .toSet()
    }

    /**
     * [value] as a JSON object, or null where it does not read back as one — which covers a value that is
     * not JSON at all, one that is JSON of any other shape, and one the reader refuses to walk.
     *
     * **Every failure of the reader is the same answer**, because this is the boundary where a value a
     * client sent becomes something this server can act on: a document exceeding the nesting depth
     * Jackson guards arrives as a `RuntimeException` rather than an `IOException`, and an unreadable
     * value that escaped as one would leave the authorize endpoint answering a `500` to a string anybody
     * can send. Only the read is inside the catch, so no failure of this server's own is swallowed.
     */
    private fun readObjectOrNull(value: String): JsonNode? {
        val node = try {
            objectMapper.readValue(value, JsonNode::class.java)
        } catch (_: Exception) {
            return null
        }
        return node?.takeIf(JsonNode::isObject)
    }

    private fun invalidRequestedClaims(): BusinessException = businessExceptionOf(
        detailsId = "claim.parse_requested.invalid",
        descriptionId = "description.claim.parse_requested.invalid"
    )
}
