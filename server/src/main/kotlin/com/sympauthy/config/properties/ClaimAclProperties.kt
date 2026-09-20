package com.sympauthy.config.properties

/**
 * Shared interface for the ACL a claim, or the template it takes its defaults from, configures.
 *
 * It carries no `@ConfigurationProperties` of its own: that annotation anchors a nested class to the
 * prefix of the class it is nested in, so one annotated interface could not serve two owners. Each owner
 * nests its own annotated `acl` interface extending this one, so the fields are declared once here and
 * the annotation once per owner.
 */
interface ClaimAclProperties {
    val consentScope: String?
    val readableByUserWhenConsented: String?
    val writableByUserWhenConsented: String?
    val readableByClientWhenConsented: String?
    val writableByClientWhenConsented: String?
    val readableWithClientScopesUnconditionally: List<String>?
    val writableWithClientScopesUnconditionally: List<String>?
}
