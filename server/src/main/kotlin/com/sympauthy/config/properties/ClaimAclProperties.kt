package com.sympauthy.config.properties

import io.micronaut.core.annotation.Nullable

/**
 * Shared interface for the ACL a claim, or the template it takes its defaults from, configures.
 *
 * It carries no `@ConfigurationProperties` of its own: that annotation anchors a nested class to the
 * prefix of the class it is nested in, so one annotated interface could not serve two owners. Each owner
 * nests its own annotated `acl` interface extending this one, so the fields are declared once here and
 * the annotation once per owner.
 */
interface ClaimAclProperties {
    @get:Nullable
    val consentScope: String?
    @get:Nullable
    val readableByPersonWhenConsented: String?
    @get:Nullable
    val collectedInFlowWhenConsented: String?
    @get:Nullable
    val writableByPersonWhenConsented: String?
    @get:Nullable
    val readableByClientWhenConsented: String?
    @get:Nullable
    val writableByClientWhenConsented: String?
    @get:Nullable
    val readableWithClientScopesUnconditionally: List<String>?
    @get:Nullable
    val writableWithClientScopesUnconditionally: List<String>?
    @get:Nullable
    val writeMaxAuthenticationAge: String?
}
