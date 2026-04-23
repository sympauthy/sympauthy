package com.sympauthy.config.properties

/**
 * Shared interface for claim ACL configuration properties.
 *
 * Used by both [ClaimConfigurationProperties.AclConfig] and
 * [ClaimTemplateConfigurationProperties.AclConfig] to avoid duplicating the field definitions.
 *
 * Each `@EachProperty` class must declare its own `@ConfigurationProperties("acl")` inner
 * interface (Micronaut requirement), but both extend this shared interface so that the
 * [com.sympauthy.config.factory.ClaimAclFactory] can accept either.
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
