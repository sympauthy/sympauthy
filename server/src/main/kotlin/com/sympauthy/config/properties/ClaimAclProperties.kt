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
    val collectedInFlow: String?
    @get:Nullable
    val readableByPerson: String?
    @get:Nullable
    val writableByPerson: String?
    @get:Nullable
    val readableByClient: String?
    @get:Nullable
    val writableByClient: String?
}
