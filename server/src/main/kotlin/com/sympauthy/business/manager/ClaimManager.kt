package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.ClaimsConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException

/**
 * Provides unrestricted access to all claim definitions configured on this authorization server.
 *
 * No scope-based filtering is applied here. When listing claims to present to the end-user during the
 * authorization flow, use [com.sympauthy.business.manager.user.ConsentAwareClaimManager] instead, which
 * filters claims on the end-user's consented scopes.
 */
@Singleton
class ClaimManager(
    @Inject private val uncheckedClaimsConfig: ClaimsConfig,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val objectMapper: ObjectMapper
) {

    private val cachedClaimsMap by lazy {
        uncheckedClaimsConfig.orThrow().claims
            .associateBy { it.id }
    }

    /**
     * Return the [Claim] identified by [id] or null.
     *
     * Backed by a map built once, so looking up many ids in a row costs no more than the lookups.
     */
    fun findByIdOrNull(id: String): Claim? {
        return cachedClaimsMap[id]
    }

    /**
     * Return all [Claim] enabled on this authorization server.
     */
    fun listEnabledClaims(): List<Claim> {
        return cachedClaimsMap.values.filter { it.enabled }
    }

    /**
     * Return all [Claim] configured on this authorization server, including disabled ones.
     */
    fun listAllClaims(): List<Claim> {
        return cachedClaimsMap.values.toList()
    }

    /**
     * List all [Claim] that we want to present to the end-user during the authentication flow.
     */
    fun listClaimsCollectedInFlow(): List<Claim> {
        return listEnabledClaims().filter(Claim::collectedInFlow)
    }

    /**
     * Return all [Claim] required to be provided by the end-user during its authorization flow.
     */
    fun listRequiredClaims(): List<Claim> {
        return listEnabledClaims().filter(Claim::required)
    }

    /**
     * Return all [Claim] configured as identifier claims.
     */
    fun listIdentifierClaims(): List<Claim> {
        return uncheckedAuthConfig.orThrow()
            .identifierClaims
            .mapNotNull { findByIdOrNull(it) }
    }

    /**
     * Return the claims [uncheckedClaims] names per OpenID channel, reading the `claims` request parameter
     * of OpenID Connect Core §5.5. A client that sent none passes null and gets [RequestedClaims.NONE].
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
            .filter { findByIdOrNull(it)?.enabled == true }
            .toSet()
    }

    /**
     * [value] as a JSON object, or null where it does not read back as one — which covers a value that is
     * not JSON at all and one that is JSON of any other shape.
     */
    private fun readObjectOrNull(value: String): JsonNode? {
        val node = try {
            objectMapper.readValue(value, JsonNode::class.java)
        } catch (_: IOException) {
            return null
        }
        return node?.takeIf(JsonNode::isObject)
    }

    private fun invalidRequestedClaims(): BusinessException = businessExceptionOf(
        detailsId = "claim.parse_requested.invalid",
        descriptionId = "description.claim.parse_requested.invalid"
    )
}
