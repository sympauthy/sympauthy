package com.sympauthy.config.parsing

import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimAclProperties
import jakarta.inject.Singleton

/**
 * Parsed ACL data shared between template ACL and full claim ACL: the expression each door is opened by,
 * as the file wrote it under this claim or template. Null means it wrote nothing for the door.
 */
data class ParsedClaimAcl(
    val collectedInFlow: String?,
    val readableByPerson: String?,
    val writableByPerson: String?,
    val readableByClient: String?,
    val writableByClient: String?,
    /**
     * The doors the claim's template opens, already validated under the template's own keys, which a door
     * the claim wrote nothing for takes. [ClaimAcl.SHUT] for a template and for a claim naming none.
     */
    val template: ClaimAcl = ClaimAcl.SHUT
) {
    companion object {

        /** An ACL no file spoke for, which every field of falls back to whatever reads it. */
        val NONE = ParsedClaimAcl(null, null, null, null, null)
    }
}

/**
 * Reads ACL properties, and hands a claim the doors its template opens.
 * Does not read an expression — that is done by [com.sympauthy.config.validation.ClaimAclValidator].
 */
@Singleton
class ClaimAclParser {

    /**
     * Parse a template ACL. All fields are nullable (no defaults applied).
     */
    fun parseTemplateAcl(acl: ClaimAclProperties?): ParsedClaimAcl {
        if (acl == null) {
            return ParsedClaimAcl.NONE
        }
        return ParsedClaimAcl(
            collectedInFlow = acl.collectedInFlow,
            readableByPerson = acl.readableByPerson,
            writableByPerson = acl.writableByPerson,
            readableByClient = acl.readableByClient,
            writableByClient = acl.writableByClient
        )
    }

    /**
     * Parse a full claim ACL: the doors the claim wrote, and the ones its [template] opens for the doors it
     * wrote nothing for.
     */
    fun parseAcl(acl: ClaimAclProperties?, template: ClaimTemplate?): ParsedClaimAcl {
        return parseTemplateAcl(acl).copy(template = template?.acl ?: ClaimAcl.SHUT)
    }
}
