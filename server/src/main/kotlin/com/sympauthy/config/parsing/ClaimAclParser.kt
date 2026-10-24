package com.sympauthy.config.parsing

import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.properties.ClaimAclProperties
import jakarta.inject.Singleton

/**
 * Parsed ACL data shared between template ACL and full claim ACL: the expression each door is opened by,
 * as the file wrote it. Null means no file spoke for the door.
 */
data class ParsedClaimAcl(
    val collectedInFlow: String?,
    val readableByPerson: String?,
    val writableByPerson: String?,
    val readableByClient: String?,
    val writableByClient: String?
) {
    companion object {

        /** An ACL no file spoke for, which every field of falls back to whatever reads it. */
        val NONE = ParsedClaimAcl(null, null, null, null, null)
    }
}

/**
 * Reads ACL properties and resolves a claim's over its template's.
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
     * Parse a full claim ACL, each door falling back to the [template]'s and left shut where neither
     * opens it.
     */
    fun parseAcl(acl: ClaimAclProperties?, template: ClaimTemplate?): ParsedClaimAcl {
        val templateAcl = template?.acl
        return ParsedClaimAcl(
            collectedInFlow = acl?.collectedInFlow ?: templateAcl?.collectedInFlow,
            readableByPerson = acl?.readableByPerson ?: templateAcl?.readableByPerson,
            writableByPerson = acl?.writableByPerson ?: templateAcl?.writableByPerson,
            readableByClient = acl?.readableByClient ?: templateAcl?.readableByClient,
            writableByClient = acl?.writableByClient ?: templateAcl?.writableByClient
        )
    }
}
